# Payment Optimizer

This Java application selects optimal payment methods for a list of orders using discount rules and available limits. It also supports loyalty points and fallback strategies.

## Features

- Applies discounts from eligible payment methods
- Respects method-specific spending limits
- Falls back to loyalty points (if available)
- Optimizes remaining points usage to reduce cost
- Prints per-order assignment and total spent per method

## Usage

### 1. Re-building the JAR

```bash
gradle clean jar
```

The JAR will be created at:

```
build/libs/payment_optimizer.jar
```

### 2. Run the program

```bash
java -jar build/libs/payment_optimizer.jar <path/to/orders.json> <path/to/paymentmethods.json>
```

### Example Input

### 3. Rum the tests
```bash
gradle test
```

**orders.json**
```json
[
  { "id": "ORDER1", "value": "100.00", "promotions": ["mZysk"] },
  { "id": "ORDER2", "value": "200.00", "promotions": ["BosBankrut"] },
  { "id": "ORDER3", "value": "150.00", "promotions": ["mZysk", "BosBankrut"] },
  { "id": "ORDER4", "value": "50.00" }
]
```

**paymentmethods.json**
```json
[
  { "id": "PUNKTY", "discount": "15", "limit": "100.00" },
  { "id": "mZysk", "discount": "10", "limit": "180.00" },
  { "id": "BosBankrut", "discount": "5", "limit": "200.00" }
]
```

### Example Output

```
Order assignments:
ORDER2: BosBankrut, paid 175.00
ORDER3: mZysk, paid 135.00
ORDER1: PUNKTY, paid 85.00
ORDER4: mZysk, paid 45.00

Payment method totals:
BosBankrut: 175.00
PUNKTY: 100.00
mZysk: 180.00
```

## Structure

- `PaymentOptimizer.java` – main logic
- `orders.json`, `paymentmethods.json` – input files
- `build.gradle` – build config
- `PaymentOptimizerTest.java` – basic unit tests
