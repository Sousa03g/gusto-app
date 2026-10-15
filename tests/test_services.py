"""Testes unitários para regras de negócio e serviços de pedidos."""

import pytest
from src.exceptions import (
    InvalidCouponError,
    InvalidOrderStateError,
    OrderNotFoundError,
)
from src.models import (
    CreateOrderRequest,
    CustomerTier,
    DestinationRegion,
    OrderItem,
    OrderStatus,
)
from src.services import OrderService, PricingService


@pytest.fixture
def order_service() -> OrderService:
    service = OrderService()
    service.clear()
    return service


def test_calculate_subtotal_multiple_items():
    items = [
        OrderItem(product_id="P1", title="Teclado", quantity=2, unit_price=150.0),
        OrderItem(product_id="P2", title="Mouse", quantity=1, unit_price=80.0),
    ]
    subtotal = PricingService.calculate_subtotal(items)
    assert subtotal == 380.0


def test_tier_discounts():
    subtotal = 1000.0

    # Bronze: 0%
    assert PricingService.calculate_discounts(subtotal, CustomerTier.BRONZE) == 0.0

    # Silver: 5%
    assert PricingService.calculate_discounts(subtotal, CustomerTier.SILVER) == 50.0

    # Gold: 10%
    assert PricingService.calculate_discounts(subtotal, CustomerTier.GOLD) == 100.0

    # Platinum: 15%
    assert PricingService.calculate_discounts(subtotal, CustomerTier.PLATINUM) == 150.0


def test_coupon_promo10():
    subtotal = 200.0
    # Gold tier gets 10% (R$ 20). Remainder is 180. Promo10 gets 10% of 180 = R$ 18.
    # Total discount: 20 + 18 = 38.
    discount = PricingService.calculate_discounts(
        subtotal=subtotal, tier=CustomerTier.GOLD, coupon_code="PROMO10"
    )
    assert discount == 38.0


def test_coupon_desconto20():
    subtotal = 100.0
    discount = PricingService.calculate_discounts(
        subtotal=subtotal, tier=CustomerTier.BRONZE, coupon_code="DESCONTO20"
    )
    assert discount == 20.0


def test_coupon_fretegratis_discount_amount():
    subtotal = 100.0
    discount = PricingService.calculate_discounts(
        subtotal=subtotal, tier=CustomerTier.BRONZE, coupon_code="FRETEGRATIS"
    )
    assert discount == 0.0


def test_invalid_coupon_raises_error():
    with pytest.raises(InvalidCouponError):
        PricingService.calculate_discounts(
            subtotal=100.0, tier=CustomerTier.BRONZE, coupon_code="CUPOM_FALSO"
        )


def test_shipping_free_threshold():
    items = [
        OrderItem(
            product_id="P1",
            title="Monitor",
            quantity=1,
            unit_price=250.0,
            weight_kg=4.0,
        )
    ]
    subtotal = 250.0
    shipping = PricingService.calculate_shipping(
        subtotal=subtotal, items=items, region=DestinationRegion.SUDESTE
    )
    assert shipping == 0.0


def test_shipping_fretegratis_coupon():
    items = [
        OrderItem(
            product_id="P1",
            title="Mousepad",
            quantity=1,
            unit_price=50.0,
            weight_kg=0.5,
        )
    ]
    subtotal = 50.0
    shipping = PricingService.calculate_shipping(
        subtotal=subtotal,
        items=items,
        region=DestinationRegion.NORTE,
        coupon_code="FRETEGRATIS",
    )
    assert shipping == 0.0


def test_shipping_calculation_by_weight_and_region():
    # Base rate SUDESTE = 15.0; weight = 2 items * 1.5kg = 3.0kg -> 3.0 * 2.0 = 6.0.
    # Total shipping = 21.0
    items = [
        OrderItem(
            product_id="P1",
            title="Livro",
            quantity=2,
            unit_price=30.0,
            weight_kg=1.5,
        )
    ]
    subtotal = 60.0
    shipping = PricingService.calculate_shipping(
        subtotal=subtotal, items=items, region=DestinationRegion.SUDESTE
    )
    assert shipping == 21.0


def test_create_order_full_lifecycle(order_service: OrderService):
    request = CreateOrderRequest(
        customer_id="CUST-100",
        customer_tier=CustomerTier.SILVER,
        items=[
            OrderItem(
                product_id="P1",
                title="Headset",
                quantity=1,
                unit_price=100.0,
                weight_kg=1.0,
            )
        ],
        region=DestinationRegion.SUDESTE,
        coupon_code="PROMO10",
    )

    summary = order_service.create_order(request)
    assert summary.order_id.startswith("ORD-")
    assert summary.status == OrderStatus.DRAFT
    assert summary.subtotal == 100.0
    # Silver (5%) = 5.0. Remainder 95. Promo10 = 9.5. Total discount = 14.5
    assert summary.discount_amount == 14.5
    # Shipping = 15.0 + (1.0 * 2.0) = 17.0
    assert summary.shipping_fee == 17.0
    # Total = (100 - 14.5) + 17 = 102.5
    assert summary.total == 102.5

    # Confirm order
    confirmed = order_service.confirm_order(summary.order_id)
    assert confirmed.status == OrderStatus.CONFIRMED

    # Cannot confirm again
    with pytest.raises(InvalidOrderStateError):
        order_service.confirm_order(summary.order_id)


def test_cancel_order_lifecycle(order_service: OrderService):
    request = CreateOrderRequest(
        customer_id="CUST-200",
        customer_tier=CustomerTier.BRONZE,
        items=[
            OrderItem(
                product_id="P1",
                title="Cabo HDMI",
                quantity=1,
                unit_price=25.0,
            )
        ],
    )
    summary = order_service.create_order(request)
    cancelled = order_service.cancel_order(summary.order_id)
    assert cancelled.status == OrderStatus.CANCELLED

    # Cannot cancel again
    with pytest.raises(InvalidOrderStateError):
        order_service.cancel_order(summary.order_id)


def test_get_non_existent_order_raises(order_service: OrderService):
    with pytest.raises(OrderNotFoundError):
        order_service.get_order("ORD-NON-EXISTENT")
