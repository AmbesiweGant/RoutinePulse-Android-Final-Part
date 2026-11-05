package com.routinepulse.app

import com.routinepulse.app.data.remote.ApiErrorParser
import com.routinepulse.app.data.remote.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiErrorParserTest {

    @Test fun maps_status_codes_to_error_kinds() {
        assertEquals(ErrorKind.UNAUTHORIZED, ApiErrorParser.kindForStatus(401))
        assertEquals(ErrorKind.NOT_FOUND, ApiErrorParser.kindForStatus(404))
        assertEquals(ErrorKind.CONFLICT, ApiErrorParser.kindForStatus(409))
        assertEquals(ErrorKind.VALIDATION, ApiErrorParser.kindForStatus(400))
        assertEquals(ErrorKind.VALIDATION, ApiErrorParser.kindForStatus(422))
        assertEquals(ErrorKind.SERVER, ApiErrorParser.kindForStatus(503))
        assertEquals(ErrorKind.UNKNOWN, ApiErrorParser.kindForStatus(418))
    }

    @Test fun reads_the_servers_error_message() {
        val error = ApiErrorParser.fromHttp(401, """{"error":"Invalid email or password"}""")
        assertEquals(ErrorKind.UNAUTHORIZED, error.kind)
        assertEquals("Invalid email or password", error.message)
    }

    @Test fun non_json_body_gives_no_message() {
        val error = ApiErrorParser.fromHttp(502, "<html>Bad gateway</html>")
        assertEquals(ErrorKind.SERVER, error.kind)
        assertNull(error.message)
    }

    @Test fun null_or_blank_message_is_ignored() {
        assertNull(ApiErrorParser.fromHttp(404, null).message)
        assertNull(ApiErrorParser.fromHttp(400, """{"error":"  "}""").message)
    }
}
