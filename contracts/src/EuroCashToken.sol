// SPDX-License-Identifier: Apache-2.0
pragma solidity 0.8.28;

import {ERC20Upgradeable} from "@openzeppelin/contracts-upgradeable/token/ERC20/ERC20Upgradeable.sol";
import {AccessControlUpgradeable} from "@openzeppelin/contracts-upgradeable/access/AccessControlUpgradeable.sol";
import {PausableUpgradeable} from "@openzeppelin/contracts-upgradeable/utils/PausableUpgradeable.sol";
import {UUPSUpgradeable} from "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";

/**
 * Eurosystem cash token: 1:1 representation of central bank money blocked on the DLT's RTGS
 * technical account. Amounts in euro cents (2 decimals).
 *
 * Permissioned network: participants do not sign. Only Eurosystem-operated roles issue, destroy
 * and execute the settlement instructions received via the EII; direct ERC-20 transfers and
 * approvals are therefore disabled.
 *
 * Upgradeable using the UUPS pattern (ERC-1967): this contract is the implementation, deployed behind an
 * ERC1967Proxy that holds the address and the state. The contract-specific state lives in an ERC-7201
 * namespace so that no later version can overwrite it.
 *
 */
contract EuroCashToken is ERC20Upgradeable, AccessControlUpgradeable, PausableUpgradeable, UUPSUpgradeable {
    bytes32 public constant MINTER_ROLE = keccak256("MINTER_ROLE");
    bytes32 public constant BURNER_ROLE = keccak256("BURNER_ROLE");
    bytes32 public constant SETTLER_ROLE = keccak256("SETTLER_ROLE");
    bytes32 public constant PAUSER_ROLE = keccak256("PAUSER_ROLE");
    bytes32 public constant UPGRADER_ROLE = keccak256("UPGRADER_ROLE");

    /// @custom:storage-location erc7201:pontes.storage.EuroCashToken
    struct EuroCashTokenStorage {
        /// Idempotency: a business reference can only be executed once.
        mapping(bytes32 => bool) processed;
    }

    // keccak256(abi.encode(uint256(keccak256("pontes.storage.EuroCashToken")) - 1)) & ~bytes32(uint256(0xff))
    bytes32 private constant STORAGE_LOCATION = 0xc2e80cf68605f7a3f12f352ad9fb37ab14dcec8219634bb766fb48d20550cf00;

    event Minted(address indexed to, uint256 amount, string ref);
    event Burned(address indexed from, uint256 amount, string ref);
    event Settled(address indexed from, address indexed to, uint256 amount, string ref);

    error AlreadyProcessed(string ref);
    error InvalidAmount();
    error DirectTransferDisabled();

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    function initialize(address admin, address operator) external initializer {
        __ERC20_init("Eurosystem Cash Token", "EURCT");
        __AccessControl_init();
        __Pausable_init();
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(PAUSER_ROLE, admin);
        _grantRole(UPGRADER_ROLE, admin);
        _grantRole(MINTER_ROLE, operator);
        _grantRole(BURNER_ROLE, operator);
        _grantRole(SETTLER_ROLE, operator);
    }

    modifier once(string calldata ref, uint256 amount) {
        _consume(ref, amount);
        _;
    }

    /// Version of the implementation currently active behind the proxy.
    function version() public pure virtual returns (string memory) {
        return "1";
    }

    function decimals() public pure override returns (uint8) {
        return 2;
    }

    function processed(bytes32 key) external view returns (bool) {
        return _getEuroCashTokenStorage().processed[key];
    }

    function mint(address to, uint256 amount, string calldata ref)
        external onlyRole(MINTER_ROLE) once(ref, amount)
    {
        _mint(to, amount);
        emit Minted(to, amount, ref);
    }

    function burn(address from, uint256 amount, string calldata ref)
        external onlyRole(BURNER_ROLE) once(ref, amount)
    {
        _burn(from, amount);
        emit Burned(from, amount, ref);
    }

    /// Cash leg of a DvP (option A): atomic buyer -> seller transfer.
    function settle(address from, address to, uint256 amount, string calldata ref)
        external onlyRole(SETTLER_ROLE) once(ref, amount)
    {
        _transfer(from, to, amount);
        emit Settled(from, to, amount, ref);
    }

    function pause() external onlyRole(PAUSER_ROLE) {
        _pause();
    }

    function unpause() external onlyRole(PAUSER_ROLE) {
        _unpause();
    }

    function transfer(address, uint256) public pure override returns (bool) {
        revert DirectTransferDisabled();
    }

    function transferFrom(address, address, uint256) public pure override returns (bool) {
        revert DirectTransferDisabled();
    }

    function approve(address, uint256) public pure override returns (bool) {
        revert DirectTransferDisabled();
    }

    function _consume(string calldata ref, uint256 amount) private {
        if (amount == 0) revert InvalidAmount();
        // forge-lint: disable-next-line(asm-keccak256)
        bytes32 key = keccak256(bytes(ref));
        EuroCashTokenStorage storage $ = _getEuroCashTokenStorage();
        if ($.processed[key]) revert AlreadyProcessed(ref);
        $.processed[key] = true;
    }

    /// Single choke point for every movement (mint, burn, settle): blocked while the contract is paused.
    function _update(address from, address to, uint256 value) internal virtual override whenNotPaused {
        super._update(from, to, value);
    }

    /// Only the UPGRADER_ROLE holder can change the implementation (in production: a multisig timelock).
    function _authorizeUpgrade(address) internal override onlyRole(UPGRADER_ROLE) {}

    function _getEuroCashTokenStorage() private pure returns (EuroCashTokenStorage storage $) {
        assembly {
            $.slot := STORAGE_LOCATION
        }
    }
}
