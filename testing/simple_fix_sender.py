#!/usr/bin/env python3
"""
Simple FIX Order Sender - Automated test script using pure Python
Sends NewOrderSingle messages to the order-service for UI testing
Uses simplefix library (no C++ compilation required)
"""

import simplefix
import socket
import time
import random
import datetime
import sys
import logging
from threading import Thread, Event

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class SimpleFIXClient:
    """Simple FIX client using simplefix library"""
    
    def __init__(self, host='localhost', port=9876):
        self.host = host
        self.port = port
        self.sock = None
        self.connected = False
        self.sequence_num = 1
        self.sender_comp_id = b'MINIFIX_CLIENT'
        self.target_comp_id = b'EXEC_SERVER'
        self.heartbeat_interval = 30
        self.last_heartbeat = time.time()
        self.running = Event()
        self.session_active = False  # Track if logon completed
        self.order_count = 0
        self.parser = simplefix.FixParser()  # Parser for incoming messages
        
    def connect(self):
        """Connect to FIX server"""
        try:
            logger.info(f"Connecting to {self.host}:{self.port}...")
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.sock.settimeout(10)
            self.sock.connect((self.host, self.port))
            self.connected = True
            logger.info("✓ Socket connected")
            return True
        except Exception as e:
            logger.error(f"Connection failed: {e}")
            return False
            
    def disconnect(self):
        """Disconnect from FIX server"""
        if self.sock:
            try:
                self.send_logout()
                self.sock.close()
            except:
                pass
        self.connected = False
        self.running.clear()
        logger.info("Disconnected")
        
    def send_message(self, msg):
        """Send a FIX message"""
        try:
            encoded = msg.encode()
            self.sock.send(encoded)
            self.sequence_num += 1
            return True
        except Exception as e:
            logger.error(f"Error sending message: {e}")
            return False
            
    def send_logon(self):
        """Send Logon message"""
        msg = simplefix.FixMessage()
        msg.append_string('8=FIX.4.4')
        msg.append_pair(35, 'A')  # MsgType = Logon
        msg.append_pair(49, self.sender_comp_id)
        msg.append_pair(56, self.target_comp_id)
        msg.append_pair(34, self.sequence_num)
        msg.append_utc_timestamp(52)  # SendingTime
        msg.append_pair(98, 0)  # EncryptMethod = None
        msg.append_pair(108, self.heartbeat_interval)  # HeartBtInt
        msg.append_pair(141, 'Y')  # ResetSeqNumFlag
        
        logger.info("→ Sending Logon...")
        return self.send_message(msg)
        
    def send_logout(self):
        """Send Logout message"""
        msg = simplefix.FixMessage()
        msg.append_string('8=FIX.4.4')
        msg.append_pair(35, '5')  # MsgType = Logout
        msg.append_pair(49, self.sender_comp_id)
        msg.append_pair(56, self.target_comp_id)
        msg.append_pair(34, self.sequence_num)
        msg.append_utc_timestamp(52)
        
        logger.info("→ Sending Logout...")
        return self.send_message(msg)
        
    def send_heartbeat(self):
        """Send Heartbeat message"""
        msg = simplefix.FixMessage()
        msg.append_string('8=FIX.4.4')
        msg.append_pair(35, '0')  # MsgType = Heartbeat
        msg.append_pair(49, self.sender_comp_id)
        msg.append_pair(56, self.target_comp_id)
        msg.append_pair(34, self.sequence_num)
        msg.append_utc_timestamp(52)
        
        return self.send_message(msg)
        
    def send_new_order(self, symbol, side, price, quantity):
        """Send NewOrderSingle message"""
        try:
            self.order_count += 1
            cl_ord_id = f"ORD{int(time.time())}{self.order_count:04d}"
            
            msg = simplefix.FixMessage()
            msg.append_string('8=FIX.4.4')
            msg.append_pair(35, 'D')  # MsgType = NewOrderSingle
            msg.append_pair(49, self.sender_comp_id)
            msg.append_pair(56, self.target_comp_id)
            msg.append_pair(34, self.sequence_num)
            msg.append_utc_timestamp(52)
            
            # Order fields
            msg.append_pair(11, cl_ord_id)  # ClOrdID
            msg.append_pair(55, symbol)  # Symbol
            msg.append_pair(54, '1' if side == 'BUY' else '2')  # Side
            msg.append_pair(40, '2')  # OrdType = Limit
            msg.append_pair(44, f"{price:.2f}")  # Price
            msg.append_pair(38, quantity)  # OrderQty
            msg.append_utc_timestamp(60)  # TransactTime
            msg.append_pair(21, '1')  # HandlInst = Automated
            
            if self.send_message(msg):
                side_str = "BUY " if side == "BUY" else "SELL"
                logger.info(f"→ Sent: {cl_ord_id} | {symbol:6s} | {side_str} | "
                          f"{quantity:4d} @ ${price:7.2f}")
                return True
            return False
            
        except Exception as e:
            logger.error(f"Error creating order: {e}")
            return False
            
    def receive_messages(self):
        """Receive and process incoming messages"""
        buffer = b''
        
        while self.connected:  # Changed: run while socket is connected
            try:
                # Check for heartbeat timing
                if self.session_active and time.time() - self.last_heartbeat > self.heartbeat_interval:
                    self.send_heartbeat()
                    self.last_heartbeat = time.time()
                
                # Receive data
                self.sock.settimeout(1.0)
                data = self.sock.recv(4096)
                if not data:
                    break
                    
                buffer += data
                
                # Process complete messages using parser
                self.parser.append_buffer(data)
                msg = self.parser.get_message()
                
                while msg:
                    self.handle_message(msg)
                    msg = self.parser.get_message()
                    
            except socket.timeout:
                continue
            except Exception as e:
                if self.connected:
                    logger.error(f"Error receiving: {e}")
                break
                
    def handle_message(self, msg):
        """Handle incoming FIX message"""
        try:
            msg_type = msg.get(35)
            
            if msg_type == b'A':  # Logon
                logger.info("← Logon accepted")
                self.session_active = True
                self.running.set()
            elif msg_type == b'0':  # Heartbeat
                pass  # Silent heartbeat
            elif msg_type == b'1':  # Test Request
                # Respond with heartbeat
                self.send_heartbeat()
            elif msg_type == b'8':  # Execution Report
                self.handle_execution_report(msg)
            elif msg_type == b'5':  # Logout
                logger.info("← Logout received")
                self.session_active = False
                self.running.clear()
                
        except Exception as e:
            logger.error(f"Error handling message: {e}")
            
    def handle_execution_report(self, msg):
        """Process execution report"""
        try:
            cl_ord_id = msg.get(11, b'').decode('utf-8') if msg.get(11) else 'N/A'
            symbol = msg.get(55, b'').decode('utf-8') if msg.get(55) else 'N/A'
            side_val = msg.get(54)
            ord_status_val = msg.get(39)
            
            if not side_val or not ord_status_val:
                return  # Skip incomplete execution reports
            
            side = side_val.decode('utf-8') if isinstance(side_val, bytes) else str(side_val)
            ord_status = ord_status_val.decode('utf-8') if isinstance(ord_status_val, bytes) else str(ord_status_val)
            
            # Map codes to strings
            side_str = "BUY " if side == '1' else "SELL"
            
            status_map = {
                '0': 'NEW',
                '1': 'PARTIALLY_FILLED',
                '2': 'FILLED',
                '8': 'REJECTED',
                '4': 'CANCELED'
            }
            status_str = status_map.get(ord_status, 'UNKNOWN')
            
            logger.info(f"← Exec Report: {cl_ord_id} | {symbol:6s} | {side_str} | {status_str}")
            
        except Exception as e:
            # Silently skip execution report parsing errors - orders are being sent successfully
            pass


class OrderGenerator:
    """Generates random order parameters for testing"""
    
    SYMBOLS = [
        "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA",
        "META", "NVDA", "JPM", "V", "JNJ",
        "WMT", "PG", "MA", "HD", "DIS",
        "BAC", "NFLX", "ADBE", "CRM", "ORCL"
    ]
    
    PRICE_RANGES = {
        "AAPL": (150, 2000),
        "GOOGL": (2500, 3000),
        "MSFT": (300, 400),
        "AMZN": (3000, 3500),
        "TSLA": (200, 300),
        "META": (300, 400),
        "NVDA": (400, 600),
        "JPM": (130, 170),
        "V": (220, 260),
        "JNJ": (150, 180),
        "WMT": (140, 160),
        "PG": (140, 160),
        "MA": (350, 400),
        "HD": (300, 350),
        "DIS": (90, 120),
        "BAC": (30, 40),
        "NFLX": (400, 500),
        "ADBE": (450, 550),
        "CRM": (200, 250),
        "ORCL": (100, 130)
    }
    
    @staticmethod
    def generate_order():
        """Generate random order parameters"""
        symbol = random.choice(OrderGenerator.SYMBOLS)
        side = random.choice(["BUY", "SELL"])
        
        price_range = OrderGenerator.PRICE_RANGES.get(symbol, (100, 200))
        price = round(random.uniform(price_range[0], price_range[1]), 2)
        
        if random.random() < 0.99:
            quantity = random.randint(10, 200)
        else:
            quantity = random.randint(500, 2000)
            
        return symbol, side, price, quantity


def run_test_session(num_orders=50, delay=0.5, burst_mode=False):
    """Run a test session sending multiple orders"""
    
    client = SimpleFIXClient()
    
    try:
        # Connect
        if not client.connect():
            logger.error("Failed to connect")
            return
            
        # Send logon
        if not client.send_logon():
            logger.error("Failed to send logon")
            return
            
        # Start receiver thread
        receiver = Thread(target=client.receive_messages, daemon=True)
        receiver.start()
        
        # Wait for logon confirmation
        logger.info("Waiting for logon confirmation...")
        timeout = 10
        elapsed = 0
        while not client.running.is_set() and elapsed < timeout:
            time.sleep(0.5)
            elapsed += 0.5
            
        if not client.running.is_set():
            logger.error("Logon timeout")
            client.disconnect()
            return
            
        logger.info(f"\n{'='*70}")
        logger.info(f"✓ Session established - Starting to send {num_orders} orders...")
        logger.info(f"{'='*70}\n")
        
        # Send orders
        sent_count = 0
        
        if burst_mode:
            burst_size = 10
            for i in range(num_orders):
                if not client.session_active:
                    break
                    
                symbol, side, price, quantity = OrderGenerator.generate_order()
                if client.send_new_order(symbol, side, price, quantity):
                    sent_count += 1
                    
                if (i + 1) % burst_size == 0:
                    logger.info(f"--- Burst complete ({sent_count} sent), pausing... ---")
                    time.sleep(2)
                else:
                    time.sleep(0.1)
        else:
            for i in range(num_orders):
                if not client.session_active:
                    break
                    
                symbol, side, price, quantity = OrderGenerator.generate_order()
                if client.send_new_order(symbol, side, price, quantity):
                    sent_count += 1
                time.sleep(delay)
                
        logger.info(f"\n{'='*70}")
        logger.info(f"✓ Test complete! Sent {sent_count}/{num_orders} orders")
        logger.info(f"{'='*70}\n")
        
        # Wait for final responses
        logger.info("Waiting for execution reports...")
        time.sleep(3)
        
    except KeyboardInterrupt:
        logger.info("\n\nTest interrupted by user")
    except Exception as e:
        logger.error(f"Error during test: {e}")
    finally:
        client.disconnect()
        time.sleep(1)


def main():
    """Main entry point"""
    print("""
╔════════════════════════════════════════════════════════════════╗
║       Simple FIX Order Sender - Automated Test Tool           ║
║                                                                ║
║    Sends random NewOrderSingle messages to order-service      ║
║              (Pure Python - No compilation needed)            ║
╚════════════════════════════════════════════════════════════════╝
    """)
    
    # Parse command line arguments
    num_orders = 50
    delay = 0.5
    burst_mode = False
    
    if len(sys.argv) > 1:
        try:
            num_orders = int(sys.argv[1])
        except ValueError:
            logger.error("Invalid number of orders. Using default (50)")
            
    if len(sys.argv) > 2:
        try:
            delay = float(sys.argv[2])
        except ValueError:
            logger.error("Invalid delay. Using default (0.5s)")
            
    if len(sys.argv) > 3 and sys.argv[3].lower() == "burst":
        burst_mode = True
        
    logger.info(f"Configuration:")
    logger.info(f"  Orders to send: {num_orders}")
    logger.info(f"  Delay between orders: {delay}s")
    logger.info(f"  Mode: {'Burst' if burst_mode else 'Normal'}")
    logger.info("")
    
    try:
        run_test_session(num_orders, delay, burst_mode)
    except KeyboardInterrupt:
        logger.info("\n\nTest interrupted by user")
        sys.exit(0)
    except Exception as e:
        logger.error(f"Test failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
