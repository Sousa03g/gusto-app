"""Serviços de regras de negócio, cálculos e gestão de pedidos."""

import uuid
from typing import Dict, List, Optional
from src.models import (
    CustomerTier,
    DestinationRegion,
    OrderItem,
    OrderStatus,
    OrderSummary,
    CreateOrderRequest,
)
from src.exceptions import (
    InvalidCouponError,
    InvalidOrderStateError,
    OrderNotFoundError,
)

TIER_DISCOUNTS = {
    CustomerTier.BRONZE: 0.0,
    CustomerTier.SILVER: 0.05,
    CustomerTier.GOLD: 0.10,
    CustomerTier.PLATINUM: 0.15,
}

BASE_SHIPPING_RATES = {
    DestinationRegion.SUDESTE: 15.0,
    DestinationRegion.SUL: 18.0,
    DestinationRegion.CENTRO_OESTE: 22.0,
    DestinationRegion.NORDESTE: 28.0,
    DestinationRegion.NORTE: 32.0,
}

FREE_SHIPPING_SUBTOTAL_THRESHOLD = 200.0
RATE_PER_KG = 2.0


class PricingService:
    """Calculadora de preços, descontos e fretes."""

    @staticmethod
    def calculate_subtotal(items: List[OrderItem]) -> float:
        subtotal = sum(item.unit_price * item.quantity for item in items)
        return round(subtotal, 2)

    @staticmethod
    def calculate_discounts(
        subtotal: float, tier: CustomerTier, coupon_code: Optional[str] = None
    ) -> float:
        tier_rate = TIER_DISCOUNTS.get(tier, 0.0)
        tier_discount = round(subtotal * tier_rate, 2)

        coupon_discount = 0.0
        if coupon_code:
            code = coupon_code.strip().upper()
            if code == "PROMO10":
                coupon_discount = round((subtotal - tier_discount) * 0.10, 2)
            elif code == "DESCONTO20":
                coupon_discount = min(20.0, subtotal - tier_discount)
            elif code == "FRETEGRATIS":
                coupon_discount = 0.0  # tratado no frete
            else:
                raise InvalidCouponError(f"Cupom '{coupon_code}' não é válido.")

        total_discount = min(subtotal, round(tier_discount + coupon_discount, 2))
        return total_discount

    @staticmethod
    def calculate_shipping(
        subtotal: float,
        items: List[OrderItem],
        region: DestinationRegion,
        coupon_code: Optional[str] = None,
    ) -> float:
        if coupon_code and coupon_code.strip().upper() == "FRETEGRATIS":
            return 0.0

        if subtotal >= FREE_SHIPPING_SUBTOTAL_THRESHOLD:
            return 0.0

        base_rate = BASE_SHIPPING_RATES.get(region, 20.0)
        total_weight = sum(item.weight_kg * item.quantity for item in items)
        shipping = base_rate + (total_weight * RATE_PER_KG)
        return round(shipping, 2)


class OrderService:
    """Gerenciador do ciclo de vida dos pedidos."""

    def __init__(self, pricing_service: Optional[PricingService] = None):
        self.pricing = pricing_service or PricingService()
        self._orders: Dict[str, OrderSummary] = {}

    def create_order(self, request: CreateOrderRequest) -> OrderSummary:
        subtotal = self.pricing.calculate_subtotal(request.items)
        discount = self.pricing.calculate_discounts(
            subtotal=subtotal,
            tier=request.customer_tier,
            coupon_code=request.coupon_code,
        )
        shipping = self.pricing.calculate_shipping(
            subtotal=subtotal,
            items=request.items,
            region=request.region,
            coupon_code=request.coupon_code,
        )

        total = round(max(0.0, (subtotal - discount) + shipping), 2)
        order_id = f"ORD-{uuid.uuid4().hex[:8].upper()}"

        summary = OrderSummary(
            order_id=order_id,
            customer_id=request.customer_id,
            customer_tier=request.customer_tier,
            status=OrderStatus.DRAFT,
            items_count=sum(i.quantity for i in request.items),
            subtotal=subtotal,
            discount_amount=discount,
            shipping_fee=shipping,
            total=total,
            region=request.region,
            coupon_code=request.coupon_code,
        )
        self._orders[order_id] = summary
        return summary

    def get_order(self, order_id: str) -> OrderSummary:
        if order_id not in self._orders:
            raise OrderNotFoundError(order_id)
        return self._orders[order_id]

    def list_orders(self) -> List[OrderSummary]:
        return list(self._orders.values())

    def confirm_order(self, order_id: str) -> OrderSummary:
        order = self.get_order(order_id)
        if order.status != OrderStatus.DRAFT:
            raise InvalidOrderStateError(
                f"Pedido em status '{order.status.value}' não pode ser confirmado."
            )
        order.status = OrderStatus.CONFIRMED
        return order

    def cancel_order(self, order_id: str) -> OrderSummary:
        order = self.get_order(order_id)
        if order.status == OrderStatus.CANCELLED:
            raise InvalidOrderStateError("Pedido já está cancelado.")
        order.status = OrderStatus.CANCELLED
        return order

    def clear(self) -> None:
        """Limpa repositório (útil para testes isolados)."""
        self._orders.clear()
