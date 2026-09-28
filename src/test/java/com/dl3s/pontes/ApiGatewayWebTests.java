package com.dl3s.pontes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** EII API Gateway and REST endpoints of the other components, tested with MockMvc. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiGatewayWebTests {

    @Autowired
    private MockMvc mvc;

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Test
    void hashLinkDvpOptionT2EndToEndThroughTheApi() throws Exception {
        String seller = unique("SELLER");
        String buyer = unique("BUYER");
        String isin = unique("XS");
        openAccount(seller, "0");
        openAccount(buyer, "500000");
        mvc.perform(post("/api/market-dlt/issuances").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"party":"%s","isin":"%s","quantity":50}""".formatted(seller, isin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available").value(50));

        // Phase 1: the seller initialises at Pontes and receives the key hashes.
        String initialisation = mvc.perform(post("/api/eii/dvp").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tradeReference":"%s","seller":"%s","buyer":"%s","cashAmount":200000,"cashLeg":"T2"}"""
                                .formatted(unique("TRADE"), seller, buyer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INITIALISED"))
                .andExpect(jsonPath("$.executionKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String dvpId = JsonPath.read(initialisation, "$.dvpId");
        String executionKeyHash = JsonPath.read(initialisation, "$.executionKeyHash");
        String cancellationKeyHash = JsonPath.read(initialisation, "$.cancellationKeyHash");
        String timeout = JsonPath.read(initialisation, "$.timeout");

        // The seller locks their securities in a Hash-Link Contract on the market DLT.
        mvc.perform(post("/api/market-dlt/hash-link-contracts").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dvpId":"%s","seller":"%s","buyer":"%s","isin":"%s","quantity":20,
                                 "executionKeyHash":"%s","cancellationKeyHash":"%s","timeout":"%s"}"""
                                .formatted(dvpId, seller, buyer, isin, executionKeyHash, cancellationKeyHash, timeout)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        // Phase 2a: the buyer pays; in option B, T2 finality is asynchronous.
        mvc.perform(post("/api/eii/dvp/{id}/payment", dvpId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payer":"%s"}""".formatted(buyer)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mvc.perform(get("/api/eii/dvp/{id}", dvpId))
                        .andExpect(jsonPath("$.status").value("SETTLED")));

        // A third party cannot obtain a key.
        mvc.perform(post("/api/eii/dvp/{id}/reveal-key", dvpId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requester":"INTRUDER"}"""))
                .andExpect(status().isForbidden());

        String revealed = mvc.perform(post("/api/eii/dvp/{id}/reveal-key", dvpId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requester":"%s"}""".formatted(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyType").value("EXECUTION"))
                .andReturn().getResponse().getContentAsString();
        String executionKey = JsonPath.read(revealed, "$.key");

        mvc.perform(post("/api/market-dlt/hash-link-contracts/{id}/execute", dvpId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"%s"}""".formatted(executionKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"));

        mvc.perform(get("/api/target/accounts/{id}", buyer))
                .andExpect(jsonPath("$.balance").value(300000));
        mvc.perform(get("/api/market-dlt/holdings/{party}", buyer))
                .andExpect(jsonPath("$[0].isin").value(isin))
                .andExpect(jsonPath("$[0].available").value(20));
    }

    @Test
    void optionAPaymentWithInsufficientFundsReturns422() throws Exception {
        String seller = unique("SELLER");
        String buyer = unique("BUYER");
        String initialisation = mvc.perform(post("/api/eii/dvp").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tradeReference":"%s","seller":"%s","buyer":"%s","cashAmount":1000,"cashLeg":"CASH_TOKEN"}"""
                                .formatted(unique("TRADE"), seller, buyer)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String dvpId = JsonPath.read(initialisation, "$.dvpId");

        mvc.perform(post("/api/eii/dvp/{id}/payment", dvpId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payer":"%s"}""".formatted(buyer)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.paymentStatus").value("REJECTED"))
                .andExpect(jsonPath("$.executionKey").doesNotExist());
    }

    @Test
    void invalidInstructionRejectedWith400() throws Exception {
        String body = mvc.perform(post("/api/eii/dvp").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tradeReference":"","seller":"A","buyer":"B","cashAmount":0,"cashLeg":null}"""))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("400");
    }

    @Test
    void sellerEqualToBuyerRejectedWith400() throws Exception {
        mvc.perform(post("/api/eii/dvp").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tradeReference":"%s","seller":"A","buyer":"A","cashAmount":10,"cashLeg":"CASH_TOKEN"}""".formatted(unique("TRADE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Seller and buyer must be different"));
    }

    @Test
    void unknownInstructionReturns404() throws Exception {
        mvc.perform(get("/api/eii/dvp/{id}", "dvp-unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void actuatorExposesModulithStructure() throws Exception {
        mvc.perform(get("/actuator/modulith"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interop").exists());
    }

    private void openAccount(String accountId, String balance) throws Exception {
        mvc.perform(post("/api/target/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"%s","owner":"Bank %s","initialBalance":%s}"""
                                .formatted(accountId, accountId, balance)))
                .andExpect(status().isCreated());
    }
}
