// SPDX-License-Identifier: Apache-2.0
pragma solidity 0.8.28;

import {EuroCashToken} from "./EuroCashToken.sol";

/**
 * V2 of the cash token, deployed via a UUPS upgrade behind the existing proxy: adds freezing of a
 * participant's holdings (sanctions, default). A frozen participant can neither receive nor transfer tokens.
 * The new state lives in its own ERC-7201 namespace: the V1 state (balances, roles, processed
 * references) is preserved as is.
 *
 * @custom:oz-upgrades-from EuroCashToken
 */
contract EuroCashTokenV2 is EuroCashToken {
    bytes32 public constant FREEZER_ROLE = keccak256("FREEZER_ROLE");

    /// @custom:storage-location erc7201:pontes.storage.EuroCashTokenV2
    struct EuroCashTokenV2Storage {
        mapping(address => bool) frozen;
    }

    // keccak256(abi.encode(uint256(keccak256("pontes.storage.EuroCashTokenV2")) - 1)) & ~bytes32(uint256(0xff))
    bytes32 private constant STORAGE_LOCATION_V2 = 0x3f28542b25ec3319098d005f515daf62303ef0f252f0c040bbdc275f1ef11f00;

    event Frozen(address indexed account);
    event Unfrozen(address indexed account);

    error AccountFrozen(address account);

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    /// Called only once, atomically with the upgrade (upgradeToAndCall).
    function initializeV2(address freezer) external reinitializer(2) {
        _grantRole(FREEZER_ROLE, freezer);
    }

    function version() public pure override returns (string memory) {
        return "2";
    }

    function isFrozen(address account) public view returns (bool) {
        return _getV2Storage().frozen[account];
    }

    function freeze(address account) external onlyRole(FREEZER_ROLE) {
        _getV2Storage().frozen[account] = true;
        emit Frozen(account);
    }

    function unfreeze(address account) external onlyRole(FREEZER_ROLE) {
        _getV2Storage().frozen[account] = false;
        emit Unfrozen(account);
    }

    function _update(address from, address to, uint256 value) internal override {
        if (from != address(0) && isFrozen(from)) revert AccountFrozen(from);
        if (to != address(0) && isFrozen(to)) revert AccountFrozen(to);
        super._update(from, to, value);
    }

    function _getV2Storage() private pure returns (EuroCashTokenV2Storage storage $) {
        assembly {
            $.slot := STORAGE_LOCATION_V2
        }
    }
}
