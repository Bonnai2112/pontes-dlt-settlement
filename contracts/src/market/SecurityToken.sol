// SPDX-License-Identifier: Apache-2.0
pragma solidity 0.8.28;

import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";

import {IdentityRegistry} from "./IdentityRegistry.sol";

/**
 * Tokenised security of the market DLT, one contract per ISIN, following the core of ERC-3643 (T-REX):
 *
 * - only addresses verified by the Identity Registry can receive the token;
 * - agents (AGENT_ROLE) mint, burn, pause, freeze an address or part of its balance, and force transfers;
 * - holders transfer their free balance (balance minus frozen tokens) as long as neither party is frozen
 *   and the token is not paused.
 *
 * The Hash-Link Contract registry is an agent: it locks the seller's securities with freezePartialTokens,
 * so that the seller remains the registered holder until delivery, which it performs with forcedTransfer.
 *
 * Not implemented from ERC-3643: modular compliance, ONCHAINID, recovery and batch functions.
 * Amounts are whole units (0 decimals).
 */
contract SecurityToken is ERC20, AccessControl, Pausable {
    bytes32 public constant AGENT_ROLE = keccak256("AGENT_ROLE");

    IdentityRegistry private immutable _identityRegistry;
    mapping(address => bool) private _frozen;
    mapping(address => uint256) private _frozenTokens;

    event AddressFrozen(address indexed userAddress, bool indexed isFrozen, address indexed owner);
    event TokensFrozen(address indexed userAddress, uint256 amount);
    event TokensUnfrozen(address indexed userAddress, uint256 amount);

    error NotVerified(address userAddress);
    error AddressIsFrozen(address userAddress);
    error InsufficientFreeBalance(address userAddress, uint256 free, uint256 needed);
    error InsufficientFrozenTokens(address userAddress, uint256 frozen, uint256 needed);

    constructor(string memory name_, string memory symbol_, IdentityRegistry identityRegistry_, address admin)
        ERC20(name_, symbol_)
    {
        _identityRegistry = identityRegistry_;
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(AGENT_ROLE, admin);
    }

    function decimals() public pure override returns (uint8) {
        return 0;
    }

    function identityRegistry() external view returns (IdentityRegistry) {
        return _identityRegistry;
    }

    function isFrozen(address userAddress) external view returns (bool) {
        return _frozen[userAddress];
    }

    function getFrozenTokens(address userAddress) external view returns (uint256) {
        return _frozenTokens[userAddress];
    }

    // ----- Holder operations -----

    function transfer(address to, uint256 amount) public override whenNotPaused returns (bool) {
        _checkHolderTransfer(msg.sender, to, amount);
        return super.transfer(to, amount);
    }

    function transferFrom(address from, address to, uint256 amount) public override whenNotPaused returns (bool) {
        _checkHolderTransfer(from, to, amount);
        return super.transferFrom(from, to, amount);
    }

    // ----- Agent operations -----

    function pause() external onlyRole(AGENT_ROLE) {
        _pause();
    }

    function unpause() external onlyRole(AGENT_ROLE) {
        _unpause();
    }

    function mint(address to, uint256 amount) external onlyRole(AGENT_ROLE) {
        _requireVerified(to);
        _mint(to, amount);
    }

    /// Burns first from the free balance, then unfreezes what is missing (as in T-REX).
    function burn(address from, uint256 amount) external onlyRole(AGENT_ROLE) {
        _unfreezeShortfall(from, amount);
        _burn(from, amount);
    }

    function setAddressFrozen(address userAddress, bool freeze) external onlyRole(AGENT_ROLE) {
        _frozen[userAddress] = freeze;
        emit AddressFrozen(userAddress, freeze, msg.sender);
    }

    function freezePartialTokens(address userAddress, uint256 amount) external onlyRole(AGENT_ROLE) {
        uint256 free = balanceOf(userAddress) - _frozenTokens[userAddress];
        if (amount > free) revert InsufficientFreeBalance(userAddress, free, amount);
        _frozenTokens[userAddress] += amount;
        emit TokensFrozen(userAddress, amount);
    }

    function unfreezePartialTokens(address userAddress, uint256 amount) external onlyRole(AGENT_ROLE) {
        _unfreeze(userAddress, amount);
    }

    /**
     * Transfer without the holder's consent, regardless of pause and address freezes: frozen tokens are
     * unfrozen as needed. The receiver must still be verified by the Identity Registry.
     */
    function forcedTransfer(address from, address to, uint256 amount) external onlyRole(AGENT_ROLE) returns (bool) {
        _requireVerified(to);
        _unfreezeShortfall(from, amount);
        _transfer(from, to, amount);
        return true;
    }

    // ----- Internals -----

    function _checkHolderTransfer(address from, address to, uint256 amount) private view {
        if (_frozen[from]) revert AddressIsFrozen(from);
        if (_frozen[to]) revert AddressIsFrozen(to);
        uint256 free = balanceOf(from) - _frozenTokens[from];
        if (amount > free) revert InsufficientFreeBalance(from, free, amount);
        _requireVerified(to);
    }

    function _requireVerified(address userAddress) private view {
        if (!_identityRegistry.isVerified(userAddress)) revert NotVerified(userAddress);
    }

    function _unfreezeShortfall(address userAddress, uint256 amount) private {
        uint256 free = balanceOf(userAddress) - _frozenTokens[userAddress];
        if (amount > free) _unfreeze(userAddress, amount - free);
    }

    function _unfreeze(address userAddress, uint256 amount) private {
        uint256 frozen = _frozenTokens[userAddress];
        if (amount > frozen) revert InsufficientFrozenTokens(userAddress, frozen, amount);
        _frozenTokens[userAddress] = frozen - amount;
        emit TokensUnfrozen(userAddress, amount);
    }
}
