// SPDX-License-Identifier: Apache-2.0
pragma solidity 0.8.28;

import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {ERC1967Utils} from "@openzeppelin/contracts/proxy/ERC1967/ERC1967Utils.sol";
import {EuroCashToken} from "../src/EuroCashToken.sol";
import {EuroCashTokenV2} from "../src/EuroCashTokenV2.sol";
import {EuroCashTokenFixture} from "./EuroCashToken.t.sol";

contract EuroCashTokenUpgradeTest is EuroCashTokenFixture {
    address freezer = makeAddr("freezer");

    function setUp() public override {
        super.setUp();
        vm.startPrank(operator);
        token.mint(bankA, 100_00, "mint-1");
        token.settle(bankA, bankB, 40_00, "dvp-1");
        vm.stopPrank();
    }

    function upgrade() internal returns (EuroCashTokenV2 v2) {
        EuroCashTokenV2 implementation = new EuroCashTokenV2();
        vm.prank(admin);
        token.upgradeToAndCall(address(implementation), abi.encodeCall(EuroCashTokenV2.initializeV2, (freezer)));
        return EuroCashTokenV2(address(token));
    }

    function test_upgradePreservesState() public {
        address proxyAddress = address(token);
        EuroCashTokenV2 v2 = upgrade();

        assertEq(address(v2), proxyAddress, "token address does not change");
        assertEq(v2.version(), "2");
        assertEq(v2.balanceOf(bankA), 60_00);
        assertEq(v2.balanceOf(bankB), 40_00);
        assertEq(v2.totalSupply(), 100_00);
        assertTrue(v2.hasRole(v2.SETTLER_ROLE(), operator));
        assertTrue(v2.processed(keccak256("dvp-1")), "V1 idempotency survives the upgrade");

        vm.expectRevert(abi.encodeWithSelector(EuroCashToken.AlreadyProcessed.selector, "dvp-1"));
        vm.prank(operator);
        v2.settle(bankA, bankB, 1_00, "dvp-1");
    }

    function test_upgradeSwitchesImplementationSlot() public {
        EuroCashTokenV2 implementation = new EuroCashTokenV2();
        vm.prank(admin);
        token.upgradeToAndCall(address(implementation), abi.encodeCall(EuroCashTokenV2.initializeV2, (freezer)));
        bytes32 slot = vm.load(address(token), ERC1967Utils.IMPLEMENTATION_SLOT);
        assertEq(address(uint160(uint256(slot))), address(implementation));
    }

    function test_onlyUpgraderCanUpgrade() public {
        EuroCashTokenV2 implementation = new EuroCashTokenV2();
        vm.expectRevert(abi.encodeWithSelector(
            IAccessControl.AccessControlUnauthorizedAccount.selector, operator, token.UPGRADER_ROLE()));
        vm.prank(operator);
        token.upgradeToAndCall(address(implementation), "");
    }

    function test_v2InitializerRunsOnce() public {
        EuroCashTokenV2 v2 = upgrade();
        vm.expectRevert();
        v2.initializeV2(bankA);
    }

    function test_frozenAccountCannotSettle() public {
        EuroCashTokenV2 v2 = upgrade();
        vm.prank(freezer);
        v2.freeze(bankA);

        vm.expectRevert(abi.encodeWithSelector(EuroCashTokenV2.AccountFrozen.selector, bankA));
        vm.prank(operator);
        v2.settle(bankA, bankB, 10_00, "dvp-2");

        vm.expectRevert(abi.encodeWithSelector(EuroCashTokenV2.AccountFrozen.selector, bankA));
        vm.prank(operator);
        v2.settle(bankB, bankA, 10_00, "dvp-3");

        vm.prank(freezer);
        v2.unfreeze(bankA);
        vm.prank(operator);
        v2.settle(bankA, bankB, 10_00, "dvp-2");
        assertEq(v2.balanceOf(bankB), 50_00);
    }

    function test_onlyFreezerCanFreeze() public {
        EuroCashTokenV2 v2 = upgrade();
        vm.expectRevert(abi.encodeWithSelector(
            IAccessControl.AccessControlUnauthorizedAccount.selector, operator, v2.FREEZER_ROLE()));
        vm.prank(operator);
        v2.freeze(bankA);
    }
}
