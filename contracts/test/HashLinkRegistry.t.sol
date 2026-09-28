// SPDX-License-Identifier: Apache-2.0
pragma solidity 0.8.28;

import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {SecurityToken} from "../src/market/SecurityToken.sol";
import {HashLinkRegistry} from "../src/market/HashLinkRegistry.sol";
import {MarketFixture} from "./SecurityToken.t.sol";

/// bankB sells 40 of its 100 securities to bankA under the Hash-Link protocol.
contract HashLinkRegistryTest is MarketFixture {
    HashLinkRegistry hashLinks;
    bytes32 constant DVP = keccak256("dvp-1");
    bytes32 constant EXECUTION_KEY = keccak256("execution-key");
    bytes32 constant CANCELLATION_KEY = keccak256("cancellation-key");
    uint64 timeout;

    function setUp() public override {
        super.setUp();
        timeout = uint64(block.timestamp + 600);
        vm.startPrank(operator);
        hashLinks = new HashLinkRegistry(operator);
        token.grantRole(token.AGENT_ROLE(), address(hashLinks));
        hashLinks.listToken(token);
        vm.stopPrank();
    }

    /// The hashes are computed first: sha256 is a precompile call, which would consume the prank or expectRevert.
    function lockAsSeller() internal {
        bytes32 executionKeyHash = sha256(abi.encodePacked(EXECUTION_KEY));
        bytes32 cancellationKeyHash = sha256(abi.encodePacked(CANCELLATION_KEY));
        vm.prank(bankB);
        hashLinks.lock(DVP, token, bankA, 40, executionKeyHash, cancellationKeyHash, timeout);
    }

    function expectLockRevert(bytes memory revertData) internal {
        bytes32 executionKeyHash = sha256(abi.encodePacked(EXECUTION_KEY));
        bytes32 cancellationKeyHash = sha256(abi.encodePacked(CANCELLATION_KEY));
        vm.expectRevert(revertData);
        vm.prank(bankB);
        hashLinks.lock(DVP, token, bankA, 40, executionKeyHash, cancellationKeyHash, timeout);
    }

    function test_lockFreezesTheSellersSecuritiesWithoutMovingThem() public {
        lockAsSeller();

        HashLinkRegistry.HashLink memory h = hashLinks.hashLink(DVP);
        assertEq(uint8(h.status), uint8(HashLinkRegistry.Status.LOCKED));
        assertEq(h.seller, bankB);
        assertEq(token.balanceOf(bankB), 100);
        assertEq(token.getFrozenTokens(bankB), 40);
    }

    function test_executionKeyDeliversToBuyer() public {
        lockAsSeller();

        vm.prank(outsider); // anyone may present the key: the destination is fixed
        hashLinks.execute(DVP, EXECUTION_KEY);

        HashLinkRegistry.HashLink memory h = hashLinks.hashLink(DVP);
        assertEq(uint8(h.status), uint8(HashLinkRegistry.Status.EXECUTED));
        assertEq(uint8(h.resolution), uint8(HashLinkRegistry.Resolution.EXECUTION_KEY));
        assertEq(token.balanceOf(bankA), 40);
        assertEq(token.balanceOf(bankB), 60);
        assertEq(token.getFrozenTokens(bankB), 0);
    }

    function test_executionStillPossibleAfterTimeout() public {
        lockAsSeller();
        vm.warp(timeout + 1);
        hashLinks.execute(DVP, EXECUTION_KEY);
        assertEq(token.balanceOf(bankA), 40);
    }

    function test_cancellationKeyOnlyAfterTimeout() public {
        lockAsSeller();

        vm.expectRevert(abi.encodeWithSelector(HashLinkRegistry.TimeoutNotReached.selector, timeout));
        hashLinks.cancel(DVP, CANCELLATION_KEY);

        vm.warp(timeout);
        hashLinks.cancel(DVP, CANCELLATION_KEY);
        HashLinkRegistry.HashLink memory h = hashLinks.hashLink(DVP);
        assertEq(uint8(h.status), uint8(HashLinkRegistry.Status.CANCELLED));
        assertEq(uint8(h.resolution), uint8(HashLinkRegistry.Resolution.CANCELLATION_KEY));
        assertEq(token.balanceOf(bankB), 100);
        assertEq(token.getFrozenTokens(bankB), 0);
    }

    function test_invalidKeysRefused() public {
        lockAsSeller();
        vm.expectRevert(HashLinkRegistry.InvalidKey.selector);
        hashLinks.execute(DVP, CANCELLATION_KEY);
        vm.warp(timeout);
        vm.expectRevert(HashLinkRegistry.InvalidKey.selector);
        hashLinks.cancel(DVP, EXECUTION_KEY);
    }

    function test_sellerConsentDeliversToBuyer() public {
        lockAsSeller();

        vm.expectRevert(HashLinkRegistry.NotSeller.selector);
        vm.prank(bankA);
        hashLinks.releaseToBuyer(DVP);

        vm.prank(bankB);
        hashLinks.releaseToBuyer(DVP);
        assertEq(uint8(hashLinks.hashLink(DVP).resolution), uint8(HashLinkRegistry.Resolution.SELLER_CONSENT));
        assertEq(token.balanceOf(bankA), 40);
    }

    function test_buyerConsentReturnsToSellerBeforeTimeout() public {
        lockAsSeller();

        vm.expectRevert(HashLinkRegistry.NotBuyer.selector);
        vm.prank(bankB);
        hashLinks.releaseToSeller(DVP);

        vm.prank(bankA);
        hashLinks.releaseToSeller(DVP);
        assertEq(uint8(hashLinks.hashLink(DVP).resolution), uint8(HashLinkRegistry.Resolution.BUYER_CONSENT));
        assertEq(token.getFrozenTokens(bankB), 0);
        assertEq(token.balanceOf(bankB), 100);
    }

    function test_unwoundOnlyOnce() public {
        lockAsSeller();
        hashLinks.execute(DVP, EXECUTION_KEY);
        vm.expectRevert(abi.encodeWithSelector(HashLinkRegistry.NotLocked.selector, DVP));
        vm.prank(bankA);
        hashLinks.releaseToSeller(DVP);
    }

    function test_lockedSecuritiesCannotBeSpentElsewhere() public {
        lockAsSeller();
        vm.expectRevert(abi.encodeWithSelector(SecurityToken.InsufficientFreeBalance.selector, bankB, 60, 61));
        vm.prank(bankB);
        token.transfer(bankA, 61);
    }

    function test_lockRequiresFreePosition() public {
        vm.prank(operator);
        token.freezePartialTokens(bankB, 70);
        expectLockRevert(abi.encodeWithSelector(HashLinkRegistry.InsufficientPosition.selector, bankB, 30, 40));
    }

    function test_lockRequiresVerifiedBuyer() public {
        vm.prank(operator);
        registry.deleteIdentity(bankA);
        expectLockRevert(abi.encodeWithSelector(HashLinkRegistry.NotVerified.selector, bankA));
    }

    function test_lockRequiresListedToken() public {
        vm.prank(operator);
        SecurityToken lookAlike = new SecurityToken("Look-alike", "XS0000000001", registry, operator);
        vm.expectRevert(abi.encodeWithSelector(HashLinkRegistry.UnlistedToken.selector, address(lookAlike)));
        vm.prank(bankB);
        hashLinks.lock(DVP, lookAlike, bankA, 1, bytes32(uint256(1)), bytes32(uint256(2)), timeout);
    }

    function test_dvpIdUsedOnlyOnce() public {
        lockAsSeller();
        expectLockRevert(abi.encodeWithSelector(HashLinkRegistry.AlreadyExists.selector, DVP));
    }

    function test_onlyOperatorListsTokens() public {
        vm.expectRevert(abi.encodeWithSelector(
            IAccessControl.AccessControlUnauthorizedAccount.selector, bankB, hashLinks.OPERATOR_ROLE()));
        vm.prank(bankB);
        hashLinks.listToken(token);
    }
}
