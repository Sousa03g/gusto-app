"""Aplicação FastAPI para a API OrderFlow."""

from typing import List
from fastapi import FastAPI, HTTPException, status
from src import __version__
from src.exceptions import (
    InvalidCouponError,
    InvalidOrderStateError,
    OrderNotFoundError,
)
from src.models import (
    CreateOrderRequest,
    HealthResponse,
    OrderSummary,
)
from src.services import OrderService

app = FastAPI(
    title="OrderFlow API",
    description="API para cálculo, processamento e gestão de qualidade de pedidos.",
    version=__version__,
)

order_service = OrderService()


@app.get("/health", response_model=HealthResponse, tags=["Monitoramento"])
def health_check():
    """Endpoint de verificação de integridade e liveness probe."""
    return HealthResponse(
        status="healthy", service="OrderFlow API", version=__version__
    )


@app.post(
    "/orders",
    response_model=OrderSummary,
    status_code=status.HTTP_201_CREATED,
    tags=["Pedidos"],
)
def create_order(request: CreateOrderRequest):
    """Cria um novo pedido calculando subtotal, descontos e frete."""
    try:
        return order_service.create_order(request)
    except InvalidCouponError as err:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(err))


@app.get("/orders", response_model=List[OrderSummary], tags=["Pedidos"])
def list_orders():
    """Lista todos os pedidos registrados em memória."""
    return order_service.list_orders()


@app.get("/orders/{order_id}", response_model=OrderSummary, tags=["Pedidos"])
def get_order(order_id: str):
    """Recupera os detalhes de um pedido pelo ID."""
    try:
        return order_service.get_order(order_id)
    except OrderNotFoundError as err:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(err))


@app.post(
    "/orders/{order_id}/confirm",
    response_model=OrderSummary,
    tags=["Pedidos"],
)
def confirm_order(order_id: str):
    """Confirma um pedido existente em estado DRAFT."""
    try:
        return order_service.confirm_order(order_id)
    except OrderNotFoundError as err:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(err))
    except InvalidOrderStateError as err:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(err))


@app.post(
    "/orders/{order_id}/cancel",
    response_model=OrderSummary,
    tags=["Pedidos"],
)
def cancel_order(order_id: str):
    """Cancela um pedido existente."""
    try:
        return order_service.cancel_order(order_id)
    except OrderNotFoundError as err:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(err))
    except InvalidOrderStateError as err:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(err))
