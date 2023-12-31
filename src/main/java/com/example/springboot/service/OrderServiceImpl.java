package com.example.springboot.service;

import com.example.springboot.dto.OrderDto;
import com.example.springboot.dto.OrderItemDto;
import com.example.springboot.exception.EntityNotFoundException;
import com.example.springboot.mapper.OrderItemMapper;
import com.example.springboot.mapper.OrderMapper;
import com.example.springboot.model.CartItem;
import com.example.springboot.model.Order;
import com.example.springboot.model.OrderItem;
import com.example.springboot.model.User;
import com.example.springboot.repository.book.order.OrderRepository;
import com.example.springboot.repository.cartitem.CartItemRepository;
import com.example.springboot.repository.orderitem.OrderItemRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    public OrderDto completeOrder(Long userId, String address) {
        Order order = new Order();
        User user = new User();
        user.setId(userId);
        order.setUser(user);
        order.setShippingAddress(address);
        order.setStatus(Order.Status.PENDING);
        Set<CartItem> cartItems = cartItemRepository.findAllByShoppingCartId(userId);
        if (cartItems.isEmpty()) {
            throw new EntityNotFoundException("Shopping cart is empty");
        }
        Set<OrderItem> orderItems;
        orderItems = cartItems.stream()
                        .map(item -> orderParser(item, order))
                        .collect(Collectors.toSet());
        order.setOrderItems(orderItems);
        BigDecimal setTotal = cartItems.stream()
                .map(i -> i.getBook().getPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotal(setTotal);
        order.setOrderDate(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);
        orderItemRepository.saveAll(orderItems);
        cartItemRepository.deleteAll(cartItems);
        return orderMapper.toDto(savedOrder);
    }

    @Override
    public List<OrderItemDto> getOrderItems(Long orderId, Long userId) {
        Optional<Order> order = orderRepository.findById(orderId);
        if (order.isPresent() && order.get().getUser().getId().equals(userId)) {
            return order.get().getOrderItems().stream()
                    .map(orderItemMapper::toDto)
                    .toList();
        }
        throw new EntityNotFoundException("Can't find order with id " + orderId);
    }

    @Override
    public OrderItemDto getOrderItem(Long orderId, Long itemId, Long userId) {
        List<OrderItemDto> orderItems = getOrderItems(orderId, userId);
        Optional<OrderItemDto> itemDto = orderItems.stream()
                .filter(item -> Objects.equals(item.getId(), itemId))
                .findFirst();
        if (itemDto.isPresent()) {
            return itemDto.get();
        }
        throw new EntityNotFoundException("Can't find item with id " + itemId);
    }

    @Override
    public List<OrderDto> getAllOrder(Long userId) {
        return orderRepository.findAll().stream()
                .map(orderMapper::toDto)
                .toList();
    }

    @Override
    public Order.Status update(Long orderId, Order.Status status) {
        Optional<Order> order = orderRepository.findById(orderId);
        if (order.isPresent()) {
            order.get().setStatus(status);
            orderRepository.save(order.get());
            return status;
        }
        throw new EntityNotFoundException("Can't find order with id " + orderId);
    }

    private OrderItem orderParser(CartItem cartItem, Order order) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setBook(cartItem.getBook());
        orderItem.setPrice(cartItem.getBook().getPrice());
        orderItem.setQuantity(cartItem.getQuantity());
        return orderItem;
    }
}
