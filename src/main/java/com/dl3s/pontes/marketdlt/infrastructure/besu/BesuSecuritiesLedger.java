package com.dl3s.pontes.marketdlt.infrastructure.besu;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;

import com.dl3s.pontes.marketdlt.HashLinkContractView;
import com.dl3s.pontes.marketdlt.HashLinkTerms;
import com.dl3s.pontes.marketdlt.HoldingView;
import com.dl3s.pontes.marketdlt.ParticipantView;
import com.dl3s.pontes.marketdlt.SecuritiesLedger;

/**
 * Market DLT on Hyperledger Besu, a chain distinct from the Eurosystem DLT: one ERC-3643 token
 * ({@code SecurityToken}) per ISIN, an {@code IdentityRegistry} of the onboarded participants and the
 * {@code HashLinkRegistry} of the Hash-Link Contracts, agent of every token.
 *
 * The market DLT operator deploys the contracts at startup, onboards the participants and issues the securities.
 * The participants sign their own transactions: the seller locks, and each party signs its consent. Presenting
 * a key needs no identity, so the operator submits it on behalf of the party (URD §4.2, footnote 8).
 * The contracts enforce the rules; this adapter only translates their rejections into business exceptions.
 */
@Component
@Profile("market-besu")
@EnableConfigurationProperties(MarketBesuProperties.class)
@Transactional
class BesuSecuritiesLedger implements SecuritiesLedger, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(BesuSecuritiesLedger.class);

    // Zero-fee network (zeroBaseFee + min-gas-price=0): accounts need no balance.
    private static final BigInteger GAS_PRICE = BigInteger.ZERO;
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(8_000_000);
    private static final byte[] AGENT_ROLE = Hash.sha3("AGENT_ROLE".getBytes(StandardCharsets.UTF_8));
    private static final List<String> STATUSES = List.of("NONE", "LOCKED", "EXECUTED", "CANCELLED");
    private static final List<String> RESOLUTIONS =
            List.of("NONE", "EXECUTION_KEY", "CANCELLATION_KEY", "SELLER_CONSENT", "BUYER_CONSENT");
    private static final HexFormat HEX = HexFormat.of();

    private final MarketBesuProperties properties;
    private final MarketAccountRepository accounts;
    private final ListedSecurityRepository listedSecurities;
    private final Web3j web3j;
    private final Credentials operator;
    private final PollingTransactionReceiptProcessor receipts;
    private final Map<String, FastRawTransactionManager> txManagers = new HashMap<>();
    private String identityRegistry;
    private String hashLinkRegistry;

    BesuSecuritiesLedger(MarketBesuProperties properties, MarketAccountRepository accounts,
                         ListedSecurityRepository listedSecurities) {
        this.properties = properties;
        this.accounts = accounts;
        this.listedSecurities = listedSecurities;
        this.web3j = Web3j.build(new HttpService(properties.rpcUrl()));
        this.operator = Credentials.create(properties.operatorPrivateKey());
        this.receipts = new PollingTransactionReceiptProcessor(web3j, 250, 80);
    }

    /** Fresh contracts at each startup, consistent with the in-memory database of the POC. */
    @Override
    public void afterPropertiesSet() throws IOException {
        identityRegistry = deploy("IdentityRegistry", new Address(operator.getAddress()));
        hashLinkRegistry = deploy("HashLinkRegistry", new Address(operator.getAddress()));
        log.info("Market DLT on Besu {}: IdentityRegistry {}, HashLinkRegistry {} (operator {})",
                properties.rpcUrl(), identityRegistry, hashLinkRegistry, operator.getAddress());
    }

    @Override
    public void destroy() {
        web3j.shutdown();
    }

    // ----- Identity Registry -----

    @Override
    public ParticipantView onboard(String party) {
        MarketAccount account = accountOf(party);
        if (!isVerified(account.getAddress())) {
            send(operator, identityRegistry, new Function("registerIdentity",
                    List.of(new Address(account.getAddress())), List.of()));
        }
        return new ParticipantView(party, account.getAddress());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantView> participants() {
        return accounts.findAll().stream()
                .filter(a -> isVerified(a.getAddress()))
                .map(a -> new ParticipantView(a.getParty(), a.getAddress()))
                .toList();
    }

    // ----- Securities -----

    @Override
    public HoldingView issue(String party, String isin, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be strictly positive");
        }
        String token = listedSecurities.findById(isin).map(ListedSecurity::getAddress).orElseGet(() -> list(isin));
        MarketAccount holder = accountOf(party);
        send(operator, token, new Function("mint",
                List.of(new Address(holder.getAddress()), new Uint256(quantity)), List.of()));
        return holding(party, holder.getAddress(), isin, token);
    }

    /** A new ISIN gets its own ERC-3643 token, whose agent is the Hash-Link registry, listed on that registry. */
    private String list(String isin) {
        String token = deploy("SecurityToken", new Utf8String("Tokenised security " + isin), new Utf8String(isin),
                new Address(identityRegistry), new Address(operator.getAddress()));
        send(operator, token, new Function("grantRole",
                List.of(new Bytes32(AGENT_ROLE), new Address(hashLinkRegistry)), List.of()));
        send(operator, hashLinkRegistry, new Function("listToken", List.of(new Address(token)), List.of()));
        listedSecurities.save(new ListedSecurity(isin, token));
        log.info("Security {} listed on the market DLT: ERC-3643 token {}", isin, token);
        return token;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HoldingView> holdingsOf(String party) {
        return accounts.findById(party).stream()
                .flatMap(account -> listedSecurities.findAll().stream()
                        .map(s -> holding(party, account.getAddress(), s.getIsin(), s.getAddress())))
                .filter(h -> h.available() > 0 || h.locked() > 0)
                .toList();
    }

    private HoldingView holding(String party, String address, String isin, String token) {
        long balance = callUint(token, "balanceOf", new Address(address)).longValueExact();
        long frozen = callUint(token, "getFrozenTokens", new Address(address)).longValueExact();
        return new HoldingView(party, isin, balance - frozen, frozen);
    }

    // ----- Hash-Link Contracts -----

    @Override
    public HashLinkContractView lock(HashLinkTerms terms) {
        OnChainHashLink existing = read(terms.dvpId());
        if (existing.exists()) {
            if (!existing.executionKeyHash().equalsIgnoreCase(terms.executionKeyHash())
                    || !existing.cancellationKeyHash().equalsIgnoreCase(terms.cancellationKeyHash())) {
                throw new IllegalStateException("Hash-Link Contract " + terms.dvpId() + " already created with different hashes");
            }
            return toView(terms.dvpId(), existing);
        }
        ListedSecurity security = listedSecurities.findById(terms.isin())
                .orElseThrow(() -> new IllegalStateException("Insufficient securities position for " + terms.seller()));
        MarketAccount buyer = accountOf(terms.buyer());
        accountOf(terms.seller());
        send(MarketAccount.credentialsOf(terms.seller()), hashLinkRegistry, new Function("lock", List.of(
                dvpKey(terms.dvpId()), new Address(security.getAddress()), new Address(buyer.getAddress()),
                new Uint256(terms.quantity()), bytes32(terms.executionKeyHash(), "Invalid executionKeyHash"),
                bytes32(terms.cancellationKeyHash(), "Invalid cancellationKeyHash"),
                new Uint64(terms.timeout().getEpochSecond())), List.of()));
        return contract(terms.dvpId());
    }

    @Override
    @Transactional(readOnly = true)
    public HashLinkContractView contract(String dvpId) {
        OnChainHashLink hashLink = read(dvpId);
        if (!hashLink.exists()) {
            throw new NoSuchElementException("No Hash-Link Contract for " + dvpId);
        }
        return toView(dvpId, hashLink);
    }

    @Override
    public HashLinkContractView execute(String dvpId, String executionKey) {
        contract(dvpId);
        send(operator, hashLinkRegistry, new Function("execute",
                List.of(dvpKey(dvpId), bytes32(executionKey, "Invalid Execution Key")), List.of()));
        return contract(dvpId);
    }

    @Override
    public HashLinkContractView cancel(String dvpId, String cancellationKey) {
        contract(dvpId);
        send(operator, hashLinkRegistry, new Function("cancel",
                List.of(dvpKey(dvpId), bytes32(cancellationKey, "Invalid Cancellation Key")), List.of()));
        return contract(dvpId);
    }

    @Override
    public HashLinkContractView releaseToBuyer(String dvpId, String requester) {
        contract(dvpId);
        send(MarketAccount.credentialsOf(requester), hashLinkRegistry,
                new Function("releaseToBuyer", List.of(dvpKey(dvpId)), List.of()));
        return contract(dvpId);
    }

    @Override
    public HashLinkContractView releaseToSeller(String dvpId, String requester) {
        contract(dvpId);
        send(MarketAccount.credentialsOf(requester), hashLinkRegistry,
                new Function("releaseToSeller", List.of(dvpKey(dvpId)), List.of()));
        return contract(dvpId);
    }

    private record OnChainHashLink(String token, String seller, String buyer, long quantity, String executionKeyHash,
                                   String cancellationKeyHash, Instant timeout, String status, String resolution) {
        boolean exists() {
            return !status.equals("NONE");
        }
    }

    private OnChainHashLink read(String dvpId) {
        List<Type> values = call(hashLinkRegistry, new Function("hashLink", List.of(dvpKey(dvpId)), List.of(
                new TypeReference<Address>() { }, new TypeReference<Address>() { }, new TypeReference<Address>() { },
                new TypeReference<Uint256>() { }, new TypeReference<Bytes32>() { }, new TypeReference<Bytes32>() { },
                new TypeReference<Uint64>() { }, new TypeReference<Uint8>() { }, new TypeReference<Uint8>() { })));
        return new OnChainHashLink((String) values.get(0).getValue(), (String) values.get(1).getValue(),
                (String) values.get(2).getValue(), ((BigInteger) values.get(3).getValue()).longValueExact(),
                HEX.formatHex((byte[]) values.get(4).getValue()), HEX.formatHex((byte[]) values.get(5).getValue()),
                Instant.ofEpochSecond(((BigInteger) values.get(6).getValue()).longValueExact()),
                STATUSES.get(((BigInteger) values.get(7).getValue()).intValueExact()),
                RESOLUTIONS.get(((BigInteger) values.get(8).getValue()).intValueExact()));
    }

    private HashLinkContractView toView(String dvpId, OnChainHashLink h) {
        String isin = listedSecurities.findByAddressIgnoreCase(h.token()).map(ListedSecurity::getIsin).orElse(h.token());
        return new HashLinkContractView(dvpId, partyAt(h.seller()), partyAt(h.buyer()), isin, h.quantity(),
                h.executionKeyHash(), h.cancellationKeyHash(), h.timeout(), h.status(),
                h.resolution().equals("NONE") ? null : h.resolution());
    }

    /** The contract indexes the Hash-Link Contracts by keccak256 of the Pontes DvP identifier. */
    private static Bytes32 dvpKey(String dvpId) {
        return new Bytes32(Hash.sha3(dvpId.getBytes(StandardCharsets.UTF_8)));
    }

    /** Keys and hashes are exchanged in hexadecimal and are 32 bytes long on-chain. */
    private static Bytes32 bytes32(String hex, String message) {
        try {
            byte[] bytes = HEX.parseHex(hex.startsWith("0x") ? hex.substring(2) : hex);
            if (bytes.length != 32) {
                throw new IllegalArgumentException(message);
            }
            return new Bytes32(bytes);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(message, e);
        }
    }

    // ----- Directory -----

    private MarketAccount accountOf(String party) {
        return accounts.findById(party).orElseGet(() -> accounts.save(new MarketAccount(party)));
    }

    private String partyAt(String address) {
        return accounts.findByAddressIgnoreCase(address).map(MarketAccount::getParty).orElse(address);
    }

    private boolean isVerified(String address) {
        List<Type> result = call(identityRegistry, new Function("isVerified", List.of(new Address(address)),
                List.of(new TypeReference<Bool>() { })));
        return (Boolean) result.get(0).getValue();
    }

    // ----- Web3j plumbing -----

    private String deploy(String contract, Type<?>... constructorArgs) {
        String data = readBytecode(contract) + FunctionEncoder.encodeConstructor(List.of(constructorArgs));
        TransactionReceipt receipt = submit(operator, null, data);
        if (!receipt.isStatusOK()) {
            throw new IllegalStateException("Deployment of " + contract + " reverted");
        }
        return receipt.getContractAddress();
    }

    private void send(Credentials signer, String contract, Function function) {
        TransactionReceipt receipt = submit(signer, contract, FunctionEncoder.encode(function));
        if (!receipt.isStatusOK()) {
            throw RevertTranslator.translate(receipt.getRevertReason(), function.getName(), this::partyAt);
        }
    }

    /** Submissions are serialized: the local nonce managers are not designed for concurrency. */
    private synchronized TransactionReceipt submit(Credentials signer, String to, String data) {
        FastRawTransactionManager txManager = txManagers.computeIfAbsent(signer.getAddress(),
                address -> new FastRawTransactionManager(web3j, signer, properties.chainId(), receipts));
        try {
            EthSendTransaction sent = txManager.sendTransaction(GAS_PRICE, GAS_LIMIT, to, data, BigInteger.ZERO);
            if (sent.hasError() && sent.getError().getMessage().toLowerCase().contains("nonce")) {
                log.warn("Nonce of {} out of sync ({}), resyncing from the node", signer.getAddress(),
                        sent.getError().getMessage());
                txManager.resetNonce();
                sent = txManager.sendTransaction(GAS_PRICE, GAS_LIMIT, to, data, BigInteger.ZERO);
            }
            if (sent.hasError()) {
                throw new IllegalStateException("Besu rejected the transaction: " + sent.getError().getMessage());
            }
            return receipts.waitForTransactionReceipt(sent.getTransactionHash());
        } catch (IOException | org.web3j.protocol.exceptions.TransactionException e) {
            throw new IllegalStateException("Failed to write to the market DLT", e);
        }
    }

    private BigInteger callUint(String contract, String functionName, Type<?>... inputs) {
        List<Type> result = call(contract, new Function(functionName, List.of(inputs),
                List.of(new TypeReference<Uint256>() { })));
        return (BigInteger) result.get(0).getValue();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Type> call(String contract, Function function) {
        try {
            String result = web3j.ethCall(Transaction.createEthCallTransaction(operator.getAddress(), contract,
                    FunctionEncoder.encode(function)), DefaultBlockParameterName.LATEST).send().getValue();
            return new ArrayList<>(FunctionReturnDecoder.decode(result, (List) function.getOutputParameters()));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read from the market DLT: " + function.getName(), e);
        }
    }

    private static String readBytecode(String contract) {
        try (InputStream in = new ClassPathResource("contracts/" + contract + ".bin").getInputStream()) {
            return "0x" + new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Missing bytecode of " + contract + " (run contracts/build.sh)", e);
        }
    }
}
