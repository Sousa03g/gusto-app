"""Exceções de domínio do sistema de pedidos."""


class OrderDomainError(Exception):
    """Exceção base de domínio."""

    pass


class OrderNotFoundError(OrderDomainError):
    """Lançada quando um pedido não é encontrado."""

    def __init__(self, order_id: str):
        self.order_id = order_id
        super().__init__(f"Pedido '{order_id}' não encontrado.")


class InvalidOrderStateError(OrderDomainError):
    """Lançada em transições de status inválidas."""

    pass


class InvalidCouponError(OrderDomainError):
    """Lançada quando um cupom é inválido ou expirado."""

    pass


class InvalidOrderItemError(OrderDomainError):
    """Lançada quando um item do pedido contém valores inválidos."""

    pass
