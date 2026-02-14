package com.team5;
import quickfix.Application;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.DoNotSend;
import quickfix.FieldNotFound;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.RejectLogon;
import quickfix.UnsupportedMessageType;
import quickfix.Session;
import quickfix.field.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedList;
import java.util.Queue;

public class OrderApplication implements Application {
    private final OrderBroadcaster broadcaster;
    // Track net position per symbol (buys - sells)
    private final ConcurrentHashMap<String, Double> symbolNetPosition = new ConcurrentHashMap<>();
    // Order books: buy and sell orders per symbol
    private final ConcurrentHashMap<String, Queue<PendingOrder>> buyOrders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Queue<PendingOrder>> sellOrders = new ConcurrentHashMap<>();
    
    // Inner class to track pending orders
    private static class PendingOrder {
        String clOrdID;
        double price;
        double remainingQty;
        
        PendingOrder(String clOrdID, double price, double qty) {
            this.clOrdID = clOrdID;
            this.price = price;
            this.remainingQty = qty;
        }
    }
    
    public OrderApplication() {
        this(null);
    }
    
    public OrderApplication(OrderBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }
    @Override
    public void onCreate(SessionID sessionId) {
    System.out.println("Session Created: " + sessionId);
    }
    @Override
    public void onLogon(SessionID sessionId) {
    System.out.println("LOGON Success: " + sessionId);
    }
    @Override
    public void onLogout(SessionID sessionId) {
    System.out.println("LOGOUT: " + sessionId);
    }
    @Override
    public void toAdmin(Message message, SessionID sessionId) {
    // Used for administrative messages (Heartbeats, Logons)
    }
    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound,
    IncorrectDataFormat, IncorrectTagValue, RejectLogon {
    // Received administrative messages
    }
    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
    // Outgoing business messages
    }
    @Override
    public void fromApp(Message message, SessionID sessionId) throws FieldNotFound,
    IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
    // Incoming business messages (New Orders will arrive here)
    
    // 1. Identify Message Type
    String msgType = message.getHeader().getString(MsgType.FIELD);
    
    if (msgType.equals(MsgType.ORDER_SINGLE)) {
        processNewOrder(message, sessionId);
    } else {
        System.out.println("Received unknown message type: " + msgType);
    }
    }
    
    /**
     * Process incoming NewOrderSingle messages
     */
    private void processNewOrder(Message message, SessionID sessionId) {
        try {
            // 2. Extract Fields using QuickFIX types
            String clOrdId = message.getString(ClOrdID.FIELD);
            String symbol = message.getString(Symbol.FIELD);
            char side = message.getChar(Side.FIELD);
            double qty = message.getDouble(OrderQty.FIELD);
            double price = message.getDouble(Price.FIELD);
            
            System.out.printf("ORDER RECEIVED: ID=%s Side=%s Sym=%s Px=%.2f Qty=%.0f%n",
                clOrdId, (side == '1' ? "BUY" : "SELL"), symbol, price, qty);
            
            // 3. Validation (Simple Rule: Price and Qty must be positive)
            if (qty <= 0 || price <= 0) {
                sendReject(message, sessionId, "Invalid Price or Qty");
            } else {
                acceptOrder(message, sessionId);
                
                // Try to match the order
                String orderStatus = matchOrder(clOrdId, symbol, side, price, qty);
                
                // Calculate net position (buys - sells)
                double netPosition = symbolNetPosition.getOrDefault(symbol, 0.0);
                
                // Broadcast to UI with net position
                Order order = new Order(clOrdId, symbol, side, price, qty, netPosition, orderStatus);
                if (broadcaster != null) {
                    broadcaster.broadcastOrder(order);
                }
            }
            
        } catch (FieldNotFound e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Match orders - simple price/time priority matching
     */
    private String matchOrder(String clOrdId, String symbol, char side, double price, double qty) {
        double remainingQty = qty;
        boolean isBuy = (side == '1');
        
        // Get opposite side order book
        Queue<PendingOrder> oppositeBook = isBuy ? 
            sellOrders.computeIfAbsent(symbol, k -> new LinkedList<>()) :
            buyOrders.computeIfAbsent(symbol, k -> new LinkedList<>());
        
        // Try to match against opposite side
        while (remainingQty > 0 && !oppositeBook.isEmpty()) {
            PendingOrder oppositeOrder = oppositeBook.peek();
            
            // Check if prices match (simple matching - same price)
            if (Math.abs(oppositeOrder.price - price) < 0.01) {
                double matchQty = Math.min(remainingQty, oppositeOrder.remainingQty);
                
                // Execute the match
                remainingQty -= matchQty;
                oppositeOrder.remainingQty -= matchQty;
                
                System.out.printf("✅ MATCHED: %s %.0f shares at %.2f (Buy vs Sell)%n", 
                    symbol, matchQty, price);
                
                // Remove opposite order if fully filled
                if (oppositeOrder.remainingQty <= 0) {
                    oppositeBook.poll();
                }
            } else {
                // No match at this price
                break;
            }
        }
        
        // Update net position
        double positionChange = isBuy ? qty : -qty;
        double newNetPosition = symbolNetPosition.merge(symbol, positionChange, Double::sum);
        
        // Add remaining quantity to order book if not fully filled
        String status;
        if (remainingQty > 0) {
            Queue<PendingOrder> ownBook = isBuy ?
                buyOrders.computeIfAbsent(symbol, k -> new LinkedList<>()) :
                sellOrders.computeIfAbsent(symbol, k -> new LinkedList<>());
            ownBook.add(new PendingOrder(clOrdId, price, remainingQty));
            
            if (remainingQty < qty) {
                status = "PARTIALLY_FILLED";
            } else {
                status = "NEW";
            }
        } else {
            status = "FILLED";
        }
        
        return status;
    }
    
    /**
     * Send acknowledgment for accepted orders
     */
    private void acceptOrder(Message request, SessionID sessionId) {
        try {
            // Create an ExecutionReport (MsgType = 8)
            quickfix.fix44.ExecutionReport ack = new quickfix.fix44.ExecutionReport();
            
            // Mandatory Fields mapping
            ack.set(new OrderID("GEN_" + System.currentTimeMillis())); // Server generated ID
            ack.set(new ExecID("EXEC_" + System.currentTimeMillis()));
            ack.set(new ClOrdID(request.getString(ClOrdID.FIELD))); // Echo back the Client's ID
            ack.set(new Symbol(request.getString(Symbol.FIELD)));
            ack.set(new Side(request.getChar(Side.FIELD)));
            
            // Status fields: "New"
            ack.set(new ExecType(ExecType.NEW));
            ack.set(new OrdStatus(OrdStatus.NEW));
            
            // Order details - REQUIRED for proper acknowledgment
            ack.set(new OrderQty(request.getDouble(OrderQty.FIELD)));
            ack.set(new OrdType(request.getChar(OrdType.FIELD)));
            ack.set(new Price(request.getDouble(Price.FIELD)));
            
            // Quantity accounting
            ack.set(new LeavesQty(request.getDouble(OrderQty.FIELD)));
            ack.set(new CumQty(0));
            ack.set(new AvgPx(0));
            
            // Send back to the specific session
            Session.sendToTarget(ack, sessionId);
            System.out.println("Order ACCEPTED and ACK sent: " + request.getString(ClOrdID.FIELD));
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Send reject message for invalid orders
     */
    private void sendReject(Message request, SessionID sessionId, String reason) {
        try {
            quickfix.fix44.ExecutionReport reject = new quickfix.fix44.ExecutionReport();
            
            reject.set(new OrderID("REJ_" + System.currentTimeMillis()));
            reject.set(new ExecID("EXEC_" + System.currentTimeMillis()));
            reject.set(new ClOrdID(request.getString(ClOrdID.FIELD)));
            reject.set(new Symbol(request.getString(Symbol.FIELD)));
            reject.set(new Side(request.getChar(Side.FIELD)));
            
            // Status: Rejected
            reject.set(new ExecType(ExecType.REJECTED));
            reject.set(new OrdStatus(OrdStatus.REJECTED));
            
            reject.set(new Text(reason));
            
            Session.sendToTarget(reject, sessionId);
            System.out.println("Order REJECTED: " + reason);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}