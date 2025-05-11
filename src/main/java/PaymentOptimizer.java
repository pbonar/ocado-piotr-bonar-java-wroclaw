import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class PaymentOptimizer {

    static class Order {
        String id;
        BigDecimal value;
        Set<String> promotions;
        String chosenMethod;
        BigDecimal paidAmount = BigDecimal.ZERO;

        Order(String jsonBlock) {
            this.id = extractString(jsonBlock, "id");
            this.value = new BigDecimal(extractString(jsonBlock, "value"));
            this.promotions = extractPromotions(jsonBlock);
        }

        public static String extractString(String json, String key) {
            int keyIndex = json.indexOf("\"" + key + "\"");
            if (keyIndex == -1) return "";
            int colon = json.indexOf(":", keyIndex);
            int start = json.indexOf("\"", colon + 1) + 1;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }

        public static Set<String> extractPromotions(String json) {
            Set<String> result = new HashSet<>();
            int idx = json.indexOf("promotions");
            if (idx == -1) return result;

            int start = json.indexOf("[", idx);
            int end = json.indexOf("]", start);
            if (start == -1 || end == -1 || start >= end) return result;

            String promoList = json.substring(start + 1, end);
            for (String s : promoList.split(",")) {
                s = s.trim().replace("\"", "");
                if (!s.isEmpty()) result.add(s);
            }
            return result;
        }
    }

    static class PaymentMethod {
        String id;
        BigDecimal discount;
        BigDecimal limit;

        PaymentMethod(String jsonBlock) {
            this.id = extractString(jsonBlock, "id");
            this.discount = new BigDecimal(extractString(jsonBlock, "discount"));
            this.limit = new BigDecimal(extractString(jsonBlock, "limit"));
        }

        public static String extractString(String json, String key) {
            int keyIndex = json.indexOf("\"" + key + "\"");
            if (keyIndex == -1) return "";
            int colon = json.indexOf(":", keyIndex);
            int start = json.indexOf("\"", colon + 1) + 1;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: java PaymentOptimizer <orders.json> <paymentmethods.json>");
            return;
        }

        // Read input JSON files
        String orderJson = Files.readString(Paths.get(args[0]));
        String methodJson = Files.readString(Paths.get(args[1]));

        // Parse orders and payment methods
        List<Order> orders = new ArrayList<>();
        for (String block : extractJsonBlocks(orderJson)) orders.add(new Order(block));

        List<PaymentMethod> methods = new ArrayList<>();
        for (String block : extractJsonBlocks(methodJson)) methods.add(new PaymentMethod(block));

        // Create lookup maps
        Map<String, PaymentMethod> methodMap = new HashMap<>();
        for (PaymentMethod pm : methods) methodMap.put(pm.id, pm);

        // Track remaining limits for each payment method
        Map<String, BigDecimal> remaining = new HashMap<>();
        for (PaymentMethod pm : methods) remaining.put(pm.id, pm.limit);

        final String pointsId = "PUNKTY";
        // Process orders from highest to lowest value
        orders.sort(Comparator.comparing(o -> o.value, Comparator.reverseOrder()));

        // First pass - assign best available payment methods
        for (Order order : orders) {
            BigDecimal bestSaved = BigDecimal.valueOf(-1);
            String bestMethod = null;
            BigDecimal bestCost = null;

            // Check all eligible promotions first
            for (String promo : order.promotions) {
                PaymentMethod pm = methodMap.get(promo);
                if (pm == null) continue;

                BigDecimal available = remaining.getOrDefault(pm.id, BigDecimal.ZERO);
                BigDecimal saved = order.value.multiply(pm.discount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal cost = order.value.subtract(saved);

                if (available.compareTo(cost) >= 0 && saved.compareTo(bestSaved) > 0) {
                    bestSaved = saved;
                    bestMethod = pm.id;
                    bestCost = cost;
                }
            }

            // If no promotion found, try any available method
            if (bestMethod == null) {
                for (PaymentMethod pm : methods) {
                    BigDecimal available = remaining.getOrDefault(pm.id, BigDecimal.ZERO);
                    BigDecimal cost = order.value.multiply(BigDecimal.ONE.subtract(pm.discount.divide(BigDecimal.valueOf(100)))).setScale(2, RoundingMode.HALF_UP);
                    if (available.compareTo(cost) >= 0) {
                        bestMethod = pm.id;
                        bestCost = cost;
                        break;
                    }
                }
            }

            if (bestMethod != null) {
                order.chosenMethod = bestMethod;
                order.paidAmount = bestCost;
                remaining.put(bestMethod, remaining.get(bestMethod).subtract(bestCost));
            } else {
                order.chosenMethod = "UNASSIGNED";
                order.paidAmount = order.value;
            }
        }

        // Optimization pass - try to improve assignments
        boolean changed;
        do {
            changed = false;

            for (Order order : orders) {
                if (order.chosenMethod.equals("UNASSIGNED")) continue;

                // Temporarily free up this order's payment
                BigDecimal oldPaid = order.paidAmount;
                String oldMethod = order.chosenMethod;
                remaining.put(oldMethod, remaining.get(oldMethod).add(oldPaid));

                String bestMethod = oldMethod;
                BigDecimal bestCost = oldPaid;

                // Check if we can find a better payment method now
                for (String promo : order.promotions) {
                    PaymentMethod pm = methodMap.get(promo);
                    if (pm == null) continue;

                    BigDecimal available = remaining.getOrDefault(pm.id, BigDecimal.ZERO);
                    BigDecimal saved = order.value.multiply(pm.discount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    BigDecimal cost = order.value.subtract(saved);

                    if (available.compareTo(cost) >= 0 && cost.compareTo(bestCost) < 0) {
                        bestCost = cost;
                        bestMethod = pm.id;
                    }
                }

                if (!bestMethod.equals(oldMethod)) {
                    order.chosenMethod = bestMethod;
                    order.paidAmount = bestCost;
                    remaining.put(bestMethod, remaining.get(bestMethod).subtract(bestCost));
                    changed = true;
                } else {
                    // Reassign the original payment method
                    order.chosenMethod = oldMethod;
                    order.paidAmount = oldPaid;
                    remaining.put(oldMethod, remaining.get(oldMethod).subtract(oldPaid));
                }
            }
        } while (changed);

        // Final pass - use any remaining points
        BigDecimal remainingPoints = remaining.getOrDefault(pointsId, BigDecimal.ZERO);
        Map<String, BigDecimal> totals = new TreeMap<>();

        if (remainingPoints.compareTo(BigDecimal.ZERO) > 0) {
            for (Order order : orders) {
                if (order.chosenMethod.equals(pointsId) || order.chosenMethod.equals("UNASSIGNED")) continue;

                // Apply points to reduce payment amount
                BigDecimal maxPointsUse = remainingPoints.min(order.paidAmount);
                if (maxPointsUse.compareTo(BigDecimal.ZERO) > 0) {
                    remaining.put(order.chosenMethod, remaining.get(order.chosenMethod).add(maxPointsUse));
                    order.paidAmount = order.paidAmount.subtract(maxPointsUse);
                    totals.put(pointsId, totals.getOrDefault(pointsId, BigDecimal.ZERO).add(maxPointsUse));
                    remainingPoints = remainingPoints.subtract(maxPointsUse);
                    remaining.put(pointsId, remainingPoints);
                    if (remainingPoints.compareTo(BigDecimal.ZERO) == 0) break;
                }
            }
        }

        // Calculate totals
        for (Order o : orders) {
            totals.put(o.chosenMethod, totals.getOrDefault(o.chosenMethod, BigDecimal.ZERO).add(o.paidAmount));
        }

        // Print final results
        for (Map.Entry<String, BigDecimal> e : totals.entrySet()) {
            System.out.println(e.getKey() + ": " + e.getValue().setScale(2, RoundingMode.HALF_UP));
        }
    }

    public static List<String> extractJsonBlocks(String json) {
        List<String> blocks = new ArrayList<>();
        int i = 0;
        while (i < json.length()) {
            int start = json.indexOf("{", i);
            if (start == -1) break;
            int brace = 1;
            int end = start + 1;
            while (end < json.length() && brace > 0) {
                if (json.charAt(end) == '{') brace++;
                if (json.charAt(end) == '}') brace--;
                end++;
            }
            blocks.add(json.substring(start, end));
            i = end;
        }
        return blocks;
    }
}