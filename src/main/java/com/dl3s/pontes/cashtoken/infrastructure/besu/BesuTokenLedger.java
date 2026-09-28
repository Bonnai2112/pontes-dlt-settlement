package com.dl3s.pontes.cashtoken.infrastructure.besu;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.FastRawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.utils.Numeric;

import com.dl3s.pontes.cashtoken.ChainVerification;
import com.dl3s.pontes.cashtoken.LedgerEntryView;
import com.dl3s.pontes.cashtoken.TokenTransferOutcome;
import com.dl3s.pontes.cashtoken.WalletView;
import com.dl3s.pontes.cashtoken.domain.TokenLedgerPort;

/**
 * Hyperledger Besu adapter: the cash tokens live in the {@code EuroCashToken} smart contract, deployed
 * behind an ERC-1967 proxy (UUPS pattern) whose address stays stable across upgrades.
 * Each write is a transaction signed by the operator and awaited until it is included in a block
 * (immediate finality with QBFT).
 */
@Component
@Profile("besu")
@EnableConfigurationProperties(BesuProperties.class)
@Transactional
class BesuTokenLedger implements TokenLedgerPort, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(BesuTokenLedger.class);

    // Zero-fee network (zeroBaseFee + min-gas-price=0).
    private static final BigInteger GAS_PRICE = BigInteger.ZERO;
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(3_000_000);

    private static final Event MINTED = new Event("Minted", List.of(
            new TypeReference<Address>(true) { }, new TypeReference<Uint256>() { }, new TypeReference<Utf8String>() { }));
    private static final Event BURNED = new Event("Burned", List.of(
            new TypeReference<Address>(true) { }, new TypeReference<Uint256>() { }, new TypeReference<Utf8String>() { }));
    private static final Event SETTLED = new Event("Settled", List.of(
            new TypeReference<Address>(true) { }, new TypeReference<Address>(true) { },
            new TypeReference<Uint256>() { }, new TypeReference<Utf8String>() { }));

    /** Known Solidity errors (V1, V2 and OpenZeppelin), indexed by selector, for readable rejections. */
    private static final Map<String, String> KNOWN_ERRORS = Map.of(
            selector("AccountFrozen(address)"), "account frozen on the DLT",
            selector("AlreadyProcessed(string)"), "reference already executed",
            selector("EnforcedPause()"), "contract paused",
            selector("ERC20InsufficientBalance(address,uint256,uint256)"), "insufficient balance",
            selector("AccessControlUnauthorizedAccount(address,bytes32)"), "missing role for the operator",
            selector("InvalidAmount()"), "invalid amount");

    private final BesuProperties properties;
    private final ParticipantAddressRepository addressBook;
    private final Web3j web3j;
    private final Credentials operator;
    private final FastRawTransactionManager txManager;
    private final PollingTransactionReceiptProcessor receipts;
    private String contractAddress;

    BesuTokenLedger(BesuProperties properties, ParticipantAddressRepository addressBook) {
        this.properties = properties;
        this.addressBook = addressBook;
        this.web3j = Web3j.build(new HttpService(properties.rpcUrl()));
        this.operator = Credentials.create(properties.operatorPrivateKey());
        this.receipts = new PollingTransactionReceiptProcessor(web3j, 250, 80);
        this.txManager = new FastRawTransactionManager(web3j, operator, properties.chainId(), receipts);
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        if (properties.contractAddress() != null && !properties.contractAddress().isBlank()) {
            contractAddress = properties.contractAddress();
        } else {
            contractAddress = deployBehindProxy();
        }
        log.info("EuroCashToken (UUPS proxy) on Besu {} at address {} (operator {})",
                properties.rpcUrl(), contractAddress, operator.getAddress());
    }

    /**
     * UUPS pattern: the implementation holds the logic, the ERC-1967 proxy holds the address and the state.
     * The proxy calls {@code initialize} in its creation transaction, so nobody can front-run it.
     * Later upgrades are applied to the proxy (see contracts/script/UpgradeToV2.s.sol).
     */
    private String deployBehindProxy() throws IOException {
        String implementation = submit(null, readBytecode("EuroCashToken")).getContractAddress();
        // In this POC, the operator is also the administrator (roles, pause, upgrade).
        String initialize = FunctionEncoder.encode(new Function("initialize", List.of(
                new Address(operator.getAddress()), new Address(operator.getAddress())), List.of()));
        String proxyArgs = FunctionEncoder.encodeConstructor(List.of(
                new Address(implementation), new DynamicBytes(Numeric.hexStringToByteArray(initialize))));
        String proxy = submit(null, readBytecode("ERC1967Proxy") + proxyArgs).getContractAddress();
        log.info("EuroCashToken V1 implementation deployed at {}", implementation);
        return proxy;
    }

    @Override
    public void destroy() {
        web3j.shutdown();
    }

    @Override
    public void mint(String participant, BigDecimal amount, String reference) {
        execute(new Function("mint", List.of(new Address(addressOf(participant)), new Uint256(toCents(amount)),
                new Utf8String(reference)), List.of()));
    }

    @Override
    public void burn(String participant, BigDecimal amount, String reference) {
        execute(new Function("burn", List.of(new Address(addressOf(participant)), new Uint256(toCents(amount)),
                new Utf8String(reference)), List.of()));
    }

    @Override
    public TokenTransferOutcome transfer(String reference, String from, String to, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return TokenTransferOutcome.rejected(reference, "Invalid amount");
        }
        // Pre-check for a readable business rejection; the contract checks again anyway.
        if (balanceOf(from).compareTo(amount) < 0) {
            return TokenTransferOutcome.rejected(reference, "Insufficient cash token balance for " + from);
        }
        TransactionReceipt receipt = submit(contractAddress, FunctionEncoder.encode(new Function("settle", List.of(
                new Address(addressOf(from)), new Address(addressOf(to)), new Uint256(toCents(amount)),
                new Utf8String(reference)), List.of())));
        if (!receipt.isStatusOK()) {
            return TokenTransferOutcome.rejected(reference, "Transaction reverted by the contract: " + describeRevert(receipt));
        }
        return TokenTransferOutcome.settled(reference);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal balanceOf(String participant) {
        return addressBook.findById(participant)
                .map(p -> fromCents(callUint("balanceOf", new Address(p.getAddress()))))
                .orElse(BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletView> wallets() {
        return addressBook.findAll().stream()
                .map(p -> new WalletView(p.getParticipant(), fromCents(callUint("balanceOf", new Address(p.getAddress())))))
                .toList();
    }

    /**
     * History rebuilt from the contract's business events (Minted, Burned, Settled); the
     * OpenZeppelin events (Transfer, RoleGranted, Paused…) are ignored. Mapping to the view:
     * sequence = block number, previousHash = block hash, hash = transaction hash.
     */
    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntryView> history() {
        Map<BigInteger, Instant> blockTimes = new HashMap<>();
        List<LedgerEntryView> entries = new ArrayList<>();
        for (Log eventLog : contractLogs()) {
            String topic = eventLog.getTopics().get(0);
            List<Type> data = FunctionReturnDecoder.decode(eventLog.getData(), dataParameters(topic));
            BigDecimal amount = fromCents((BigInteger) data.get(0).getValue());
            String reference = (String) data.get(1).getValue();
            String type;
            String from = null;
            String to = null;
            if (topic.equals(EventEncoder.encode(MINTED))) {
                type = "MINT";
                to = participantAt(eventLog.getTopics().get(1));
            } else if (topic.equals(EventEncoder.encode(BURNED))) {
                type = "BURN";
                from = participantAt(eventLog.getTopics().get(1));
            } else {
                type = "TRANSFER";
                from = participantAt(eventLog.getTopics().get(1));
                to = participantAt(eventLog.getTopics().get(2));
            }
            Instant recordedAt = blockTimes.computeIfAbsent(eventLog.getBlockNumber(), this::blockTimestamp);
            entries.add(new LedgerEntryView(eventLog.getBlockNumber().longValueExact(), type, from, to, amount,
                    reference, recordedAt, eventLog.getBlockHash(), eventLog.getTransactionHash()));
        }
        return entries;
    }

    /** Chain integrity is guaranteed by QBFT consensus; we check the contract's tokens in circulation. */
    @Override
    @Transactional(readOnly = true)
    public ChainVerification verify() {
        List<LedgerEntryView> entries = history();
        BigDecimal fromEvents = BigDecimal.ZERO;
        for (LedgerEntryView entry : entries) {
            if (entry.type().equals("MINT")) {
                fromEvents = fromEvents.add(entry.amount());
            } else if (entry.type().equals("BURN")) {
                fromEvents = fromEvents.subtract(entry.amount());
            }
        }
        BigDecimal totalSupply = fromCents(callUint("totalSupply"));
        return new ChainVerification(totalSupply.compareTo(fromEvents) == 0, entries.size(), totalSupply, null);
    }

    private void execute(Function function) {
        TransactionReceipt receipt = submit(contractAddress, FunctionEncoder.encode(function));
        if (!receipt.isStatusOK()) {
            throw new IllegalStateException("Transaction " + function.getName() + " reverted by the contract: "
                    + describeRevert(receipt));
        }
    }

    /** Submissions are serialized: the local nonce manager is not designed for concurrency. */
    private synchronized TransactionReceipt submit(String to, String data) {
        try {
            EthSendTransaction sent = txManager.sendTransaction(GAS_PRICE, GAS_LIMIT, to, data, BigInteger.ZERO);
            if (sent.hasError() && isStaleNonce(sent)) {
                // The same key signed outside the application (upgrade script, cast…): resync.
                log.warn("Operator nonce out of sync ({}), resyncing from the node", sent.getError().getMessage());
                txManager.resetNonce();
                sent = txManager.sendTransaction(GAS_PRICE, GAS_LIMIT, to, data, BigInteger.ZERO);
            }
            if (sent.hasError()) {
                throw new IllegalStateException("Besu rejected the transaction: " + sent.getError().getMessage());
            }
            return receipts.waitForTransactionReceipt(sent.getTransactionHash());
        } catch (IOException | org.web3j.protocol.exceptions.TransactionException e) {
            throw new IllegalStateException("Failed to write to Besu", e);
        }
    }

    private static String describeRevert(TransactionReceipt receipt) {
        String data = receipt.getRevertReason();
        if (data == null || data.length() < 10) {
            return "unknown reason";
        }
        return KNOWN_ERRORS.getOrDefault(data.substring(0, 10).toLowerCase(), data);
    }

    private static String selector(String signature) {
        return Hash.sha3String(signature).substring(0, 10);
    }

    private static boolean isStaleNonce(EthSendTransaction sent) {
        String message = sent.getError().getMessage().toLowerCase();
        return message.contains("nonce too low") || message.contains("nonce_too_low");
    }

    private BigInteger callUint(String functionName, Type<?>... inputs) {
        Function function = new Function(functionName, List.of(inputs), List.of(new TypeReference<Uint256>() { }));
        try {
            String result = web3j.ethCall(Transaction.createEthCallTransaction(operator.getAddress(), contractAddress,
                    FunctionEncoder.encode(function)), DefaultBlockParameterName.LATEST).send().getValue();
            List<Type> decoded = FunctionReturnDecoder.decode(result, function.getOutputParameters());
            return (BigInteger) decoded.get(0).getValue();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read from Besu: " + functionName, e);
        }
    }

    private List<Log> contractLogs() {
        EthFilter filter = new EthFilter(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST, contractAddress);
        // A single topic sub-filter: topic0 ∈ {Minted, Burned, Settled}.
        filter.addOptionalTopics(EventEncoder.encode(MINTED), EventEncoder.encode(BURNED), EventEncoder.encode(SETTLED));
        try {
            List<Log> logs = new ArrayList<>();
            for (EthLog.LogResult<?> result : web3j.ethGetLogs(filter).send().getLogs()) {
                logs.add((Log) result.get());
            }
            return logs;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read Besu events", e);
        }
    }

    private Instant blockTimestamp(BigInteger blockNumber) {
        try {
            var block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), false).send().getBlock();
            return Instant.ofEpochSecond(block.getTimestamp().longValueExact());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read block " + blockNumber, e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<TypeReference<Type>> dataParameters(String topic) {
        Event event = topic.equals(EventEncoder.encode(SETTLED)) ? SETTLED
                : topic.equals(EventEncoder.encode(MINTED)) ? MINTED : BURNED;
        return (List) event.getNonIndexedParameters();
    }

    private String addressOf(String participant) {
        return addressBook.findById(participant)
                .orElseGet(() -> addressBook.save(new ParticipantAddress(participant)))
                .getAddress();
    }

    private String participantAt(String topic) {
        String address = "0x" + topic.substring(topic.length() - 40);
        return addressBook.findByAddressIgnoreCase(address).map(ParticipantAddress::getParticipant).orElse(address);
    }

    private static BigInteger toCents(BigDecimal amount) {
        try {
            return amount.movePointRight(2).toBigIntegerExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount limited to 2 decimal places: " + amount);
        }
    }

    private static BigDecimal fromCents(BigInteger cents) {
        return new BigDecimal(cents, 2);
    }

    private static String readBytecode(String contract) throws IOException {
        try (InputStream in = new ClassPathResource("contracts/" + contract + ".bin").getInputStream()) {
            return "0x" + new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }
}
