"""Modelos de dados e esquemas de validação do sistema."""

from enum import Enum
from typing import List, Optional
from pydantic import BaseModel, Field, field_validator


class CustomerTier(str, Enum):
    BRONZE = "BRONZE"
    SILVER = "SILVER"
    GOLD = "GOLD"
    PLATINUM = "PLATINUM"


class OrderStatus(str, Enum):
    DRAFT = "DRAFT"
    CONFIRMED = "CONFIRMED"
    CANCELLED = "CANCELLED"


class DestinationRegion(str, Enum):
    SUL = "SUL"
    SUDESTE = "SUDESTE"
    CENTRO_OESTE = "CENTRO_OESTE"
    NORDESTE = "NORDESTE"
    NORTE = "NORTE"


class OrderItem(BaseModel):
    product_id: str = Field(..., min_length=1, description="Identificador do produto")
    title: str = Field(..., min_length=1, description="Nome do produto")
    quantity: int = Field(..., gt=0, description="Quantidade deve ser maior que zero")
    unit_price: float = Field(
        ..., gt=0.0, description="Preço unitário deve ser positivo"
    )
    weight_kg: float = Field(
        default=0.5, gt=0.0, description="Peso em quilogramas por unidade"
    )

    @field_validator("unit_price")
    @classmethod
    def validate_unit_price(cls, v: float) -> float:
        return round(v, 2)


class CreateOrderRequest(BaseModel):
    customer_id: str = Field(..., min_length=1, description="ID do cliente")
    customer_tier: CustomerTier = Field(
        default=CustomerTier.BRONZE, description="Categoria de fidelidade do cliente"
    )
    items: List[OrderItem] = Field(
        ..., min_length=1, description="Lista de itens do pedido"
    )
    region: DestinationRegion = Field(
        default=DestinationRegion.SUDESTE, description="Região de entrega"
    )
    coupon_code: Optional[str] = Field(
        default=None, description="Cupom de desconto opcional"
    )


class OrderSummary(BaseModel):
    order_id: str
    customer_id: str
    customer_tier: CustomerTier
    status: OrderStatus
    items_count: int
    subtotal: float
    discount_amount: float
    shipping_fee: float
    total: float
    region: DestinationRegion
    coupon_code: Optional[str] = None


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str
