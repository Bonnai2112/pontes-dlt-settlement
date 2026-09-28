// SPDX-License-Identifier: Apache-2.0
pragma solidity 0.8.28;

import {Script, console} from "forge-std/Script.sol";
import {EuroCashToken} from "../src/EuroCashToken.sol";
import {EuroCashTokenV2} from "../src/EuroCashTokenV2.sol";

/**
 * Upgrades the already deployed EuroCashToken proxy to V2, without changing its address or state.
 * Variables: PROXY (proxy address), UPGRADER_KEY (key holding UPGRADER_ROLE), FREEZER (address).
 */
contract UpgradeToV2 is Script {
    function run() external {
        EuroCashToken proxy = EuroCashToken(vm.envAddress("PROXY"));
        address freezer = vm.envAddress("FREEZER");
        console.log("Version before upgrade:", proxy.version());

        vm.startBroadcast(vm.envUint("UPGRADER_KEY"));
        EuroCashTokenV2 implementation = new EuroCashTokenV2();
        proxy.upgradeToAndCall(address(implementation), abi.encodeCall(EuroCashTokenV2.initializeV2, (freezer)));
        vm.stopBroadcast();

        console.log("New implementation:", address(implementation));
        console.log("Version after upgrade:", proxy.version());
    }
}
