// SPDX-License-Identifier: Apache-2.0
pragma solidity 0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

import {SecurityToken} from "./SecurityToken.sol";

/**
 * Hash-Link Contracts of the market DLT (Pontes URD §4.2): securities leg of a DvP settled in central bank money
 * by Pontes. Pontes publishes the SHA-256 hashes of two 32-byte keys; the seller locks its securities against
 * them, and the contract only releases them in one of the four cases of the URD:
 *
 * 1. the buyer consents to return them to the seller (releaseToSeller, signed by the buyer);
 * 2. the seller consents to deliver them to the buyer (releaseToBuyer, signed by the seller);
 * 3. anyone presents the Execution Key, revealed by Pontes once the cash leg has settled (execute);
 * 4. anyone presents the Cancellation Key, revealed by Pontes after the timeout (cancel, only after the timeout).
 *
 * Execution stays possible after the timeout: a buyer who paid just before it must still receive the
 * securities, and Pontes never reveals both keys for the same DvP. The destination is fixed at lock time,
 * so a key seen in the mempool cannot divert the securities.
 *
 * The securities remain registered with the seller while locked: the registry is an agent of each listed
 * ERC-3643 token and freezes them (freezePartialTokens), then delivers them with forcedTransfer.
 */
contract HashLinkRegistry is AccessControl {
    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");

    enum Status { NONE, LOCKED, EXECUTED, CANCELLED }
    enum Resolution { NONE, EXECUTION_KEY, CANCELLATION_KEY, SELLER_CONSENT, BUYER_CONSENT }

    struct HashLink {
        SecurityToken token;
        address seller;
        address buyer;
        uint256 quantity;
        bytes32 executionKeyHash;
        bytes32 cancellationKeyHash;
        uint64 timeout;
        Status status;
        Resolution resolution;
    }

    mapping(bytes32 dvpId => HashLink) private _hashLinks;
    mapping(address token => bool) private _listed;

    event TokenListed(address indexed token);
    event Locked(bytes32 indexed dvpId, address indexed seller, address indexed buyer, address token, uint256 quantity,
        uint64 timeout);
    event Executed(bytes32 indexed dvpId, bytes32 executionKey);
    event Cancelled(bytes32 indexed dvpId, bytes32 cancellationKey);
    event ReleasedToBuyer(bytes32 indexed dvpId);
    event ReleasedToSeller(bytes32 indexed dvpId);

    error UnlistedToken(address token);
    error InvalidTerms();
    error AlreadyExists(bytes32 dvpId);
    error NotVerified(address userAddress);
    error InsufficientPosition(address seller, uint256 free, uint256 needed);
    error NotLocked(bytes32 dvpId);
    error InvalidKey();
    error TimeoutNotReached(uint64 timeout);
    error NotSeller();
    error NotBuyer();

    constructor(address admin) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(OPERATOR_ROLE, admin);
    }

    /// Only tokens listed by the market DLT operator can be locked, so that nobody locks a look-alike token.
    function listToken(SecurityToken token) external onlyRole(OPERATOR_ROLE) {
        _listed[address(token)] = true;
        emit TokenListed(address(token));
    }

    function isListed(address token) external view returns (bool) {
        return _listed[token];
    }

    function hashLink(bytes32 dvpId) external view returns (HashLink memory) {
        return _hashLinks[dvpId];
    }

    /// Phase 1, step 3: the seller (msg.sender) locks its securities with the hashes received from Pontes.
    function lock(bytes32 dvpId, SecurityToken token, address buyer, uint256 quantity, bytes32 executionKeyHash,
                  bytes32 cancellationKeyHash, uint64 timeout) external {
        if (!_listed[address(token)]) revert UnlistedToken(address(token));
        if (quantity == 0 || buyer == address(0) || buyer == msg.sender || executionKeyHash == cancellationKeyHash) {
            revert InvalidTerms();
        }
        if (_hashLinks[dvpId].status != Status.NONE) revert AlreadyExists(dvpId);
        if (!token.identityRegistry().isVerified(msg.sender)) revert NotVerified(msg.sender);
        if (!token.identityRegistry().isVerified(buyer)) revert NotVerified(buyer);
        uint256 free = token.balanceOf(msg.sender) - token.getFrozenTokens(msg.sender);
        if (quantity > free) revert InsufficientPosition(msg.sender, free, quantity);

        _hashLinks[dvpId] = HashLink(token, msg.sender, buyer, quantity, executionKeyHash, cancellationKeyHash, timeout,
            Status.LOCKED, Resolution.NONE);
        token.freezePartialTokens(msg.sender, quantity);
        emit Locked(dvpId, msg.sender, buyer, address(token), quantity, timeout);
    }

    /// Case 3: delivery to the buyer against the Execution Key.
    function execute(bytes32 dvpId, bytes32 executionKey) external {
        HashLink storage h = _locked(dvpId);
        if (sha256(abi.encodePacked(executionKey)) != h.executionKeyHash) revert InvalidKey();
        _deliverToBuyer(h, Resolution.EXECUTION_KEY);
        emit Executed(dvpId, executionKey);
    }

    /// Case 4: return to the seller against the Cancellation Key, once the timeout is reached.
    function cancel(bytes32 dvpId, bytes32 cancellationKey) external {
        HashLink storage h = _locked(dvpId);
        if (sha256(abi.encodePacked(cancellationKey)) != h.cancellationKeyHash) revert InvalidKey();
        if (block.timestamp < h.timeout) revert TimeoutNotReached(h.timeout);
        _returnToSeller(h, Resolution.CANCELLATION_KEY);
        emit Cancelled(dvpId, cancellationKey);
    }

    /// Case 2: the seller gives up its protection and delivers without a key.
    function releaseToBuyer(bytes32 dvpId) external {
        HashLink storage h = _locked(dvpId);
        if (msg.sender != h.seller) revert NotSeller();
        _deliverToBuyer(h, Resolution.SELLER_CONSENT);
        emit ReleasedToBuyer(dvpId);
    }

    /// Case 1: the buyer gives up its claim and returns the securities to the seller without a key.
    function releaseToSeller(bytes32 dvpId) external {
        HashLink storage h = _locked(dvpId);
        if (msg.sender != h.buyer) revert NotBuyer();
        _returnToSeller(h, Resolution.BUYER_CONSENT);
        emit ReleasedToSeller(dvpId);
    }

    function _locked(bytes32 dvpId) private view returns (HashLink storage h) {
        h = _hashLinks[dvpId];
        if (h.status != Status.LOCKED) revert NotLocked(dvpId);
    }

    /// State first, then the token calls (checks-effects-interactions).
    function _deliverToBuyer(HashLink storage h, Resolution resolution) private {
        h.status = Status.EXECUTED;
        h.resolution = resolution;
        h.token.unfreezePartialTokens(h.seller, h.quantity);
        h.token.forcedTransfer(h.seller, h.buyer, h.quantity);
    }

    function _returnToSeller(HashLink storage h, Resolution resolution) private {
        h.status = Status.CANCELLED;
        h.resolution = resolution;
        h.token.unfreezePartialTokens(h.seller, h.quantity);
    }
}
