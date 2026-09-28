// SPDX-License-Identifier: Apache-2.0
pragma solidity 0.8.28;

import {Test} from "forge-std/Test.sol";
import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";
import {IdentityRegistry} from "../src/market/IdentityRegistry.sol";
import {SecurityToken} from "../src/market/SecurityToken.sol";

/// Market DLT fixture: an Identity Registry and one ERC-3643 token, both administered by the operator.
abstract contract MarketFixture is Test {
    IdentityRegistry registry;
    SecurityToken token;
    address operator = makeAddr("market-operator");
    address bankA = makeAddr("BANKAFRPP");
    address bankB = makeAddr("BANKBFRPP");
    address outsider = makeAddr("OUTSIDER");

    function setUp() public virtual {
        vm.startPrank(operator);
        registry = new IdentityRegistry(operator);
        token = new SecurityToken("Digital bond XS0000000001", "XS0000000001", registry, operator);
        registry.registerIdentity(bankA);
        registry.registerIdentity(bankB);
        token.mint(bankB, 100);
        vm.stopPrank();
    }
}

contract SecurityTokenTest is MarketFixture {
    function test_metadata() public view {
        assertEq(token.symbol(), "XS0000000001");
        assertEq(token.decimals(), 0);
        assertEq(address(token.identityRegistry()), address(registry));
    }

    function test_mintOnlyToVerifiedIdentities() public {
        vm.expectRevert(abi.encodeWithSelector(SecurityToken.NotVerified.selector, outsider));
        vm.prank(operator);
        token.mint(outsider, 1);
    }

    function test_onlyAgentsMint() public {
        vm.expectRevert(abi.encodeWithSelector(
            IAccessControl.AccessControlUnauthorizedAccount.selector, bankA, token.AGENT_ROLE()));
        vm.prank(bankA);
        token.mint(bankA, 1);
    }

    function test_holderTransfersToVerifiedIdentity() public {
        vm.prank(bankB);
        token.transfer(bankA, 10);
        assertEq(token.balanceOf(bankA), 10);
    }

    function test_holderCannotTransferToUnverifiedIdentity() public {
        vm.expectRevert(abi.encodeWithSelector(SecurityToken.NotVerified.selector, outsider));
        vm.prank(bankB);
        token.transfer(outsider, 10);
    }

    function test_frozenTokensCannotBeTransferred() public {
        vm.prank(operator);
        token.freezePartialTokens(bankB, 70);
        assertEq(token.getFrozenTokens(bankB), 70);

        vm.expectRevert(abi.encodeWithSelector(SecurityToken.InsufficientFreeBalance.selector, bankB, 30, 31));
        vm.prank(bankB);
        token.transfer(bankA, 31);

        vm.prank(bankB);
        token.transfer(bankA, 30);
        assertEq(token.balanceOf(bankB), 70);
    }

    function test_cannotFreezeMoreThanFreeBalance() public {
        vm.startPrank(operator);
        token.freezePartialTokens(bankB, 60);
        vm.expectRevert(abi.encodeWithSelector(SecurityToken.InsufficientFreeBalance.selector, bankB, 40, 41));
        token.freezePartialTokens(bankB, 41);
        vm.stopPrank();
    }

    function test_frozenAddressCannotTransfer() public {
        vm.prank(operator);
        token.setAddressFrozen(bankB, true);
        vm.expectRevert(abi.encodeWithSelector(SecurityToken.AddressIsFrozen.selector, bankB));
        vm.prank(bankB);
        token.transfer(bankA, 1);
    }

    function test_pauseBlocksHolderTransfers() public {
        vm.prank(operator);
        token.pause();
        vm.expectRevert(Pausable.EnforcedPause.selector);
        vm.prank(bankB);
        token.transfer(bankA, 1);
    }

    function test_forcedTransferUnfreezesAsNeededAndIgnoresPause() public {
        vm.startPrank(operator);
        token.freezePartialTokens(bankB, 90);
        token.pause();
        token.forcedTransfer(bankB, bankA, 95);
        vm.stopPrank();

        assertEq(token.balanceOf(bankA), 95);
        assertEq(token.balanceOf(bankB), 5);
        assertEq(token.getFrozenTokens(bankB), 5);
    }

    function test_forcedTransferStillRequiresVerifiedReceiver() public {
        vm.expectRevert(abi.encodeWithSelector(SecurityToken.NotVerified.selector, outsider));
        vm.prank(operator);
        token.forcedTransfer(bankB, outsider, 1);
    }

    function test_deletedIdentityCanNoLongerReceive() public {
        vm.prank(operator);
        registry.deleteIdentity(bankA);
        vm.expectRevert(abi.encodeWithSelector(SecurityToken.NotVerified.selector, bankA));
        vm.prank(bankB);
        token.transfer(bankA, 1);
    }
}
