"""Testes de integração e validação de contratos HTTP da API."""

import pytest
from fastapi.testclient import TestClient
from src.main import app, order_service


@pytest.fixture(autouse=True)
def reset_service():
    """Garante isolamento entre execuções de testes."""
    order_service.clear()
    yield
    order_service.clear()


@pytest.fixture
def client():
    return TestClient(app)


def test_health_check_endpoint(client: TestClient):
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert data["service"] == "OrderFlow API"
    assert "version" in data


def test_create_order_success(client: TestClient):
    payload = {
        "customer_id": "CLI-999",
        "customer_tier": "GOLD",
        "region": "SUDESTE",
        "coupon_code": "PROMO10",
        "items": [
            {
                "product_id": "PROD-1",
                "title": "Fone Bluetooth",
                "quantity": 2,
                "unit_price": 100.0,
                "weight_kg": 0.5,
            }
        ],
    }

    response = client.post("/orders", json=payload)
    assert response.status_code == 201
    data = response.json()
    assert data["order_id"].startswith("ORD-")
    assert data["customer_id"] == "CLI-999"
    assert data["subtotal"] == 200.0
    # Gold gives 10% (R$ 20). Promo10 gives 10% of 180 (R$ 18). Total discount = 38.
    assert data["discount_amount"] == 38.0
    # Subtotal >= 200 means free shipping:
    assert data["shipping_fee"] == 0.0
    assert data["total"] == 162.0


def test_create_order_invalid_coupon_returns_400(client: TestClient):
    payload = {
        "customer_id": "CLI-123",
        "customer_tier": "BRONZE",
        "region": "SUL",
        "coupon_code": "CUPOM_INVALIDO",
        "items": [
            {
                "product_id": "PROD-2",
                "title": "Mouse Gamer",
                "quantity": 1,
                "unit_price": 50.0,
                "weight_kg": 0.3,
            }
        ],
    }
    response = client.post("/orders", json=payload)
    assert response.status_code == 400
    assert "não é válido" in response.json()["detail"]


def test_create_order_invalid_payload_returns_422(client: TestClient):
    # Quantidade <= 0 deve falhar na validação do Pydantic
    payload = {
        "customer_id": "CLI-123",
        "customer_tier": "BRONZE",
        "region": "SUL",
        "items": [
            {
                "product_id": "PROD-2",
                "title": "Mouse Gamer",
                "quantity": 0,
                "unit_price": 50.0,
            }
        ],
    }
    response = client.post("/orders", json=payload)
    assert response.status_code == 422


def test_get_order_by_id_success_and_not_found(client: TestClient):
    # 404 test
    not_found = client.get("/orders/ORD-INEXISTENTE")
    assert not_found.status_code == 404

    # Create and fetch test
    create_res = client.post(
        "/orders",
        json={
            "customer_id": "CLI-1",
            "customer_tier": "BRONZE",
            "items": [
                {
                    "product_id": "P1",
                    "title": "Capa",
                    "quantity": 1,
                    "unit_price": 30.0,
                }
            ],
        },
    )
    order_id = create_res.json()["order_id"]

    fetch_res = client.get(f"/orders/{order_id}")
    assert fetch_res.status_code == 200
    assert fetch_res.json()["order_id"] == order_id


def test_list_orders(client: TestClient):
    assert len(client.get("/orders").json()) == 0

    client.post(
        "/orders",
        json={
            "customer_id": "CLI-1",
            "customer_tier": "BRONZE",
            "items": [
                {
                    "product_id": "P1",
                    "title": "Item 1",
                    "quantity": 1,
                    "unit_price": 20.0,
                }
            ],
        },
    )
    client.post(
        "/orders",
        json={
            "customer_id": "CLI-2",
            "customer_tier": "SILVER",
            "items": [
                {
                    "product_id": "P2",
                    "title": "Item 2",
                    "quantity": 1,
                    "unit_price": 40.0,
                }
            ],
        },
    )

    list_res = client.get("/orders")
    assert list_res.status_code == 200
    assert len(list_res.json()) == 2


def test_confirm_and_cancel_order_transitions(client: TestClient):
    create_res = client.post(
        "/orders",
        json={
            "customer_id": "CLI-1",
            "customer_tier": "BRONZE",
            "items": [
                {
                    "product_id": "P1",
                    "title": "Webcam",
                    "quantity": 1,
                    "unit_price": 120.0,
                }
            ],
        },
    )
    order_id = create_res.json()["order_id"]

    # Confirm
    confirm_res = client.post(f"/orders/{order_id}/confirm")
    assert confirm_res.status_code == 200
    assert confirm_res.json()["status"] == "CONFIRMED"

    # Confirm again -> 409 Conflict
    conflict_res = client.post(f"/orders/{order_id}/confirm")
    assert conflict_res.status_code == 409

    # Cancel a new order
    create_res2 = client.post(
        "/orders",
        json={
            "customer_id": "CLI-2",
            "customer_tier": "BRONZE",
            "items": [
                {
                    "product_id": "P2",
                    "title": "Microfone",
                    "quantity": 1,
                    "unit_price": 90.0,
                }
            ],
        },
    )
    order_id2 = create_res2.json()["order_id"]
    cancel_res = client.post(f"/orders/{order_id2}/cancel")
    assert cancel_res.status_code == 200
    assert cancel_res.json()["status"] == "CANCELLED"

    # Cancel again -> 409 Conflict
    cancel_res2 = client.post(f"/orders/{order_id2}/cancel")
    assert cancel_res2.status_code == 409

    # Non-existent order on confirm and cancel -> 404 Not Found
    assert client.post("/orders/ORD-99999/confirm").status_code == 404
    assert client.post("/orders/ORD-99999/cancel").status_code == 404
