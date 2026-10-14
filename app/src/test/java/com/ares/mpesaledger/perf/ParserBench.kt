package com.ares.mpesaledger.perf

import com.ares.mpesaledger.debug.SyntheticSms
import com.ares.mpesaledger.domain.Categorizer
import com.ares.mpesaledger.domain.DefaultRules
import com.ares.mpesaledger.parser.MpesaParser
import com.ares.mpesaledger.parser.ParseResult
import org.junit.Assert.assertTrue
import org.junit.Test

/** Not a strict benchmark: a guard that parsing 5,000 messages stays far below a second on the JVM. */
class ParserBench {
    @Test fun `parse and categorize 5000 synthetic messages`() {
        val msgs = SyntheticSms.generate(5000, seed = 42)
        val categorizer = Categorizer(DefaultRules.all)
        repeat(3) { msgs.forEach { MpesaParser.parse(it.body) } } // warm-up
        val t0 = System.nanoTime()
        var tx = 0
        msgs.forEach { m ->
            val r = MpesaParser.parse(m.body)
            if (r is ParseResult.Transaction) { tx++; categorizer.categorize(r.tx.kind, r.tx.counterparty, r.tx.account) }
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("ParserBench: ${msgs.size} messages, $tx transactions, $ms ms")
        assertTrue("parsing took $ms ms", ms < 1000)
    }
}
