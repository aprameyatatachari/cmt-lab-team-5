package com.team5;

public class Order {
    
    private String clOrdID;
    private String symbol;
    private char side;
    private double price;
    private double quantity;
    private double cumQty;
    private String ordStatus;

    // Constructor
    public Order() {
    }

    public Order(String clOrdID, String symbol, char side, double price, double quantity) {
        this.clOrdID = clOrdID;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.cumQty = 0;
        this.ordStatus = "NEW";
    }

    public Order(String clOrdID, String symbol, char side, double price, double quantity, double cumQty) {
        this.clOrdID = clOrdID;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.cumQty = cumQty;
        this.ordStatus = "NEW";
    }

    public Order(String clOrdID, String symbol, char side, double price, double quantity, double cumQty, String ordStatus) {
        this.clOrdID = clOrdID;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.cumQty = cumQty;
        this.ordStatus = ordStatus;
    }

    // Getters and Setters
    public String getClOrdID() {
        return clOrdID;
    }

    public void setClOrdID(String clOrdID) {
        this.clOrdID = clOrdID;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public char getSide() {
        return side;
    }

    public void setSide(char side) {
        this.side = side;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public double getCumQty() {
        return cumQty;
    }

    public void setCumQty(double cumQty) {
        this.cumQty = cumQty;
    }

    public String getOrdStatus() {
        return ordStatus;
    }

    public void setOrdStatus(String ordStatus) {
        this.ordStatus = ordStatus;
    }

    @Override
    public String toString() {
        return "Order{" +
                "clOrdID='" + clOrdID + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", price=" + price +
                ", quantity=" + quantity +
                ", cumQty=" + cumQty +
                ", ordStatus='" + ordStatus + '\'' +
                '}';
    }
}
