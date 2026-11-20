# Online-order coin discounts

The Android POS reads two optional fields from its existing `online_orders` query:
`coins_spent` (coins used, to one decimal place) and `coin_discount` (PHP
deducted from the order subtotal). The order card shows the original subtotal,
the coins used and the peso discount, then the net total. Older rows or
deployments without these fields still show the original total.

This is display support only. Before enabling redemption, the checkout/backend
change must supply both fields on discounted orders and update
`private.record_online_sale` in Connect to persist the same discount and net
total in POS sales history and reports. The POS does not decide how many coins
may be spent or perform any coin debits or refunds.

The web rewards editor calls the currency coins. Its existing database field
`shop_rewards.points_cost` and whole-coin reward prices are unchanged.
