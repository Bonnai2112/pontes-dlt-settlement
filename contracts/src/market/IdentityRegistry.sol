// SPDX-License-Identifier: Apache-2.0
pragma solidity 0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

/**
 * Identity Registry of the market DLT (ERC-3643): the addresses allowed to hold the securities tokens.
 *
 * Simplified compared with T-REX: the market DLT operator (agent) registers the addresses of the
 * participants it has onboarded (KYC done off-chain). There is no ONCHAINID, no claim, no trusted issuer
 * and no investor country.
 */
contract IdentityRegistry is AccessControl {
    bytes32 public constant AGENT_ROLE = keccak256("AGENT_ROLE");

    mapping(address => bool) private _verified;

    event IdentityRegistered(address indexed investorAddress);
    event IdentityRemoved(address indexed investorAddress);

    constructor(address admin) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(AGENT_ROLE, admin);
    }

    function registerIdentity(address userAddress) external onlyRole(AGENT_ROLE) {
        _verified[userAddress] = true;
        emit IdentityRegistered(userAddress);
    }

    function deleteIdentity(address userAddress) external onlyRole(AGENT_ROLE) {
        _verified[userAddress] = false;
        emit IdentityRemoved(userAddress);
    }

    function isVerified(address userAddress) external view returns (bool) {
        return _verified[userAddress];
    }
}
