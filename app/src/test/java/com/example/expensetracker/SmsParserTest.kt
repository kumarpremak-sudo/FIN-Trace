package com.example.expensetracker

import com.example.expensetracker.logic.SmsParser
import org.junit.Test
import org.junit.Assert.*

class SmsParserTest {

    @Test
    fun testParseDebitSms() {
        val sms = "Rs.500 spent on Card XX1234 at SWIGGY. Bal: Rs.1000."
        val result = SmsParser.parseBankSms(sms)
        assertTrue(result.isValidTransaction)
        assertEquals(500.0, result.amount, 0.0)
        assertEquals("DEBIT", result.type)
        assertEquals("Swiggy", result.merchant)
    }

    @Test
    fun testParseUpiDebitSms() {
        val sms = "Paid Rs.250 to VPA-ZOMATO@okaxis ref 123456789."
        val result = SmsParser.parseBankSms(sms)
        assertTrue(result.isValidTransaction)
        assertEquals(250.0, result.amount, 0.0)
        assertEquals("DEBIT", result.type)
        assertEquals("Zomato", result.merchant)
    }

    @Test
    fun testParseCreditSms() {
        val sms = "Your a/c XX123 is credited with INR 1,500.00 on 27-JUL-26 by Salary-July."
        val result = SmsParser.parseBankSms(sms)
        assertTrue(result.isValidTransaction)
        assertEquals(1500.0, result.amount, 0.0)
        assertEquals("CREDIT", result.type)
        assertEquals("Salary-July", result.merchant)
    }

    @Test
    fun testIgnoreOtpSms() {
        val sms = "OTP for transaction at Amazon is 123456. Do not share."
        val result = SmsParser.parseBankSms(sms)
        assertFalse(result.isValidTransaction)
    }

    @Test
    fun testRefundSms() {
        val sms = "Refund of Rs.120 received from SWIGGY."
        val result = SmsParser.parseBankSms(sms)
        assertTrue(result.isValidTransaction)
        assertEquals(120.0, result.amount, 0.0)
        assertEquals("REFUND", result.type)
        assertEquals("Swiggy", result.merchant)
    }

    @Test
    fun testSelfTransferSms() {
        val sms = "Rs.1000 debited for self transfer to own a/c."
        val result = SmsParser.parseBankSms(sms)
        assertTrue(result.isValidTransaction)
        assertEquals("TRANSFER", result.type)
    }

    @Test
    fun testBillReminderSms() {
        val sms = "Credit card bill due date is 05-AUG. Minimum balance Rs.500."
        val result = SmsParser.parseBankSms(sms)
        assertFalse(result.isValidTransaction)
    }

    @Test
    fun testCleanMerchantName() {
        assertEquals("Swiggy", SmsParser.cleanMerchantName("SWIGGY-1234"))
        assertEquals("Zomato", SmsParser.cleanMerchantName("zomato@hdfc"))
        assertEquals("Amazon Pay", SmsParser.cleanMerchantName("VPA-AMAZON PAY"))
    }

    @Test
    fun testParseInvestmentSms() {
        val sms = "Rs.5000 invested in NIPPON INDIA MUTUAL FUND via SIP."
        val result = SmsParser.parseBankSms(sms)
        assertTrue(result.isValidTransaction)
        assertEquals(5000.0, result.amount, 0.0)
        assertEquals("INVESTMENT", result.type)
        assertEquals("Nippon India Mutual Fund", result.merchant)
    }

    @Test
    fun testParseHdfcSpecific() {
        val sms = "Spent Rs.1844 On HDFC Bank Card 1643 At TTS NAGAR THIRUV On 2026-07-24."
        val result = SmsParser.parseBankSms(sms)
        assertTrue(result.isValidTransaction)
        assertEquals(1844.0, result.amount, 0.0)
        assertEquals("DEBIT", result.type)
        assertEquals("Tts Nagar Thiruv", result.merchant)
    }

    @Test
    fun testParseHdfcPaymentReceived() {
        val sms = "DEAR HDFCBANK CARDMEMBER, PAYMENT OF Rs. 15321.00 RECEIVED TOWARDS YOUR CREDIT CARD"
        val result = SmsParser.parseBankSms(sms)
        assertTrue(result.isValidTransaction)
        assertEquals(15321.0, result.amount, 0.0)
        assertEquals("CREDIT", result.type)
        assertEquals("Your Credit Card", result.merchant)
    }

    @Test
    fun testFailedTransactions() {
        val sms1 = "Transaction of Rs.500 failed at SWIGGY due to insufficient funds."
        val result1 = SmsParser.parseBankSms(sms1)
        assertFalse(result1.isValidTransaction)

        val sms2 = "Your transaction of Rs.1200 was declined by the bank."
        val result2 = SmsParser.parseBankSms(sms2)
        assertFalse(result2.isValidTransaction)
    }
}
