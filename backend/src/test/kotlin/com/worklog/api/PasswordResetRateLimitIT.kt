package com.worklog.api

import com.worklog.IntegrationTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = [
    "worklog.rate-limit.enabled=true",
    "worklog.rate-limit.authRequestsPerSecond=2",
    "worklog.rate-limit.authBurstSize=2"
])
@ActiveProfiles("test")


class PasswordResetRateLimitIT : IntegrationTestBase() {

    @Autowired
    lateinit var webTestClient: WebTestClient


    @Test
    fun `パスワードリセット依頼APIがレートリミット超過で429を返す`() {
        val requestBody = """{"email":"rate_limit_test@example.com"}"""
        val uri = "/api/v1/auth/password-reset/request"
        // 1分間に2回まではリセット依頼可能、それを超える3回目で429エラーとなることを検証
        repeat(2) {
            webTestClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk
        }
        // 3回目（2回制限を超過）は429（Too Many Requests）が返る
        webTestClient.post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
    }
}
