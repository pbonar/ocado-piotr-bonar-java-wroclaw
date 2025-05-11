import org.junit.jupiter.api.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class PaymentOptimizerTest {

    @Test
    void testExtractString() {
        String json = "{\"id\":\"ORDER1\",\"value\":\"100.00\"}";
        String id = PaymentOptimizer.Order.extractString(json, "id");
        String value = PaymentOptimizer.Order.extractString(json, "value");

        assertEquals("ORDER1", id);
        assertEquals("100.00", value);
    }

    @Test
    void testExtractPromotions() {
        String json = "{\"promotions\":[\"A\", \"B\"]}";
        Set<String> promotions = PaymentOptimizer.Order.extractPromotions(json);
        assertTrue(promotions.contains("A"));
        assertTrue(promotions.contains("B"));
        assertEquals(2, promotions.size());
    }

    @Test
    void testExtractJsonBlocks() {
        String input = "[{\"id\":\"A\"},{\"id\":\"B\"}]";
        List<String> blocks = PaymentOptimizer.extractJsonBlocks(input);
        assertEquals(2, blocks.size());
        assertTrue(blocks.get(0).contains("A"));
        assertTrue(blocks.get(1).contains("B"));
    }

    @Test
    void testOrderConstructor() {
        String json = "{\"id\":\"ORDER1\",\"value\":\"120.00\",\"promotions\":[\"CARD1\"]}";
        PaymentOptimizer.Order o = new PaymentOptimizer.Order(json);
        assertEquals("ORDER1", o.id);
        assertEquals(new BigDecimal("120.00"), o.value);
        assertTrue(o.promotions.contains("CARD1"));
    }

    @Test
    void testPaymentMethodConstructor() {
        String json = "{\"id\":\"CARD1\",\"discount\":\"10\",\"limit\":\"200.00\"}";
        PaymentOptimizer.PaymentMethod pm = new PaymentOptimizer.PaymentMethod(json);
        assertEquals("CARD1", pm.id);
        assertEquals(new BigDecimal("10"), pm.discount);
        assertEquals(new BigDecimal("200.00"), pm.limit);
    }
}
