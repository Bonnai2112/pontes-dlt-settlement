// SPDX-License-Identifier: Apache-2.0
pragma solidity 0.8.28;

import {Test} from "forge-std/Test.sol";
import {ERC1967Proxy} from "@openzeppelin/contracts/proxy/ERC1967/ERC1967Proxy.sol";
import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {IERC20Errors} from "@openzeppelin/contracts/interfaces/draft-IERC6093.sol";
import {PausableUpgradeable} from "@openzeppelin/contracts-upgradeable/utils/PausableUpgradeable.sol";
import {Initializable} from "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import {EuroCashToken} from "../src/EuroCashToken.sol";

/// Deploys the implementation behind an ERC1967Proxy, as in production.
abstract contract EuroCashTokenFixture is Test {
    EuroCashToken token;
    address admin = makeAddr("admin");
    address operator = makeAddr("operator");
    address bankA = makeAddr("BANKAFRPP");
    address bankB = makeAddr("BANKBFRPP");

    function setUp() public virtual {
        EuroCashToken implementation = new EuroCashToken();
        ERC1967Proxy proxy = new ERC1967Proxy(
            address(implementation), abi.encodeCall(EuroCashToken.initialize, (admin, operator)));
        token = EuroCashToken(address(proxy));
    }
}

contract EuroCashTokenTest is EuroCashTokenFixture {
    function test_cannotReinitializeProxy() public {
        vm.expectRevert(Initializable.InvalidInitialization.selector);
        token.initialize(bankA, bankA);
    }

    function test_implementationCannotBeInitialized() public {
        EuroCashToken implementation = new EuroCashToken();
        vm.expectRevert(Initializable.InvalidInitialization.selector);
        implementation.initialize(bankA, bankA);
    }

    function test_metadata() public view {
        assertEq(token.symbol(), "EURCT");
        assertEq(token.decimals(), 2);
        assertEq(token.version(), "1");
    }

    function test_mintCreditsAndEmits() public {
        vm.expectEmit(address(token));
        emit EuroCashToken.Minted(bankA, 100_00, "mint-1");
        vm.prank(operator);
        token.mint(bankA, 100_00, "mint-1");

        assertEq(token.balanceOf(bankA), 100_00);
        assertEq(token.totalSupply(), 100_00);
    }

    function test_onlyMinterCanMint() public {
        vm.expectRevert(abi.encodeWithSelector(
            IAccessControl.AccessControlUnauthorizedAccount.selector, bankA, token.MINTER_ROLE()));
        vm.prank(bankA);
        token.mint(bankA, 1, "mint-1");
    }

    function test_referenceIsExecutedOnce() public {
        vm.startPrank(operator);
        token.mint(bankA, 100_00, "ref-1");
        vm.expectRevert(abi.encodeWithSelector(EuroCashToken.AlreadyProcessed.selector, "ref-1"));
        token.mint(bankA, 100_00, "ref-1");
        vm.stopPrank();
    }

    function test_zeroAmountRejected() public {
        vm.expectRevert(EuroCashToken.InvalidAmount.selector);
        vm.prank(operator);
        token.mint(bankA, 0, "mint-0");
    }

    function test_settleMovesTokens() public {
        vm.startPrank(operator);
        token.mint(bankA, 100_00, "mint-1");
        vm.expectEmit(address(token));
        emit EuroCashToken.Settled(bankA, bankB, 40_00, "dvp-1");
        token.settle(bankA, bankB, 40_00, "dvp-1");
        vm.stopPrank();

        assertEq(token.balanceOf(bankA), 60_00);
        assertEq(token.balanceOf(bankB), 40_00);
        assertEq(token.totalSupply(), 100_00);
    }

    function test_settleRejectsInsufficientBalance() public {
        vm.startPrank(operator);
        token.mint(bankA, 10_00, "mint-1");
        vm.expectRevert(abi.encodeWithSelector(
            IERC20Errors.ERC20InsufficientBalance.selector, bankA, 10_00, 50_00));
        token.settle(bankA, bankB, 50_00, "dvp-1");
        vm.stopPrank();
    }

    function test_burnDestroysSupply() public {
        vm.startPrank(operator);
        token.mint(bankA, 100_00, "mint-1");
        token.burn(bankA, 30_00, "redeem-1");
        vm.stopPrank();

        assertEq(token.balanceOf(bankA), 70_00);
        assertEq(token.totalSupply(), 70_00);
    }

    function test_directTransfersDisabled() public {
        vm.prank(operator);
        token.mint(bankA, 100_00, "mint-1");

        vm.startPrank(bankA);
        vm.expectRevert(EuroCashToken.DirectTransferDisabled.selector);
        // forge-lint: disable-next-line(erc20-unchecked-transfer)
        token.transfer(bankB, 1);
        vm.expectRevert(EuroCashToken.DirectTransferDisabled.selector);
        token.approve(bankB, 1);
        vm.expectRevert(EuroCashToken.DirectTransferDisabled.selector);
        // forge-lint: disable-next-line(erc20-unchecked-transfer)
        token.transferFrom(bankA, bankB, 1);
        vm.stopPrank();
    }

    function test_pauseBlocksAllMovements() public {
        vm.prank(operator);
        token.mint(bankA, 100_00, "mint-1");
        vm.prank(admin);
        token.pause();

        vm.startPrank(operator);
        vm.expectRevert(PausableUpgradeable.EnforcedPause.selector);
        token.settle(bankA, bankB, 1_00, "dvp-1");
        vm.expectRevert(PausableUpgradeable.EnforcedPause.selector);
        token.mint(bankA, 1_00, "mint-2");
        vm.stopPrank();

        vm.prank(admin);
        token.unpause();
        vm.prank(operator);
        token.settle(bankA, bankB, 1_00, "dvp-1");
        assertEq(token.balanceOf(bankB), 1_00);
    }

    function test_onlyPauserCanPause() public {
        vm.expectRevert(abi.encodeWithSelector(
            IAccessControl.AccessControlUnauthorizedAccount.selector, operator, token.PAUSER_ROLE()));
        vm.prank(operator);
        token.pause();
    }

    function test_adminCanRevokeOperator() public {
        bytes32 settlerRole = token.SETTLER_ROLE();
        vm.prank(admin);
        token.revokeRole(settlerRole, operator);

        vm.expectRevert(abi.encodeWithSelector(
            IAccessControl.AccessControlUnauthorizedAccount.selector, operator, settlerRole));
        vm.prank(operator);
        token.settle(bankA, bankB, 1, "dvp-1");
    }
}
