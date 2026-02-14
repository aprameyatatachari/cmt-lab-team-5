import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WebsocketService, Order } from '../../services/websocket.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-order-grid',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './order-grid.component.html',
  styleUrls: ['./order-grid.component.css']
})
export class OrderGridComponent implements OnInit, OnDestroy {
  orders: Order[] = [];
  connected = false;
  private subscription?: Subscription;
  private connectionSubscription?: Subscription;

  constructor(private wsService: WebsocketService) {}

  ngOnInit(): void {
    // Subscribe to incoming orders
    this.subscription = this.wsService.messages$.subscribe((order) => {
      // Add new order to the top of the list with animation class
      this.orders.unshift({ ...order, isNew: true });
      
      // Remove animation class after animation completes
      setTimeout(() => {
        if (this.orders[0]) {
          this.orders[0].isNew = false;
        }
      }, 500);

      // Limit to 100 orders to prevent memory issues
      if (this.orders.length > 100) {
        this.orders = this.orders.slice(0, 100);
      }
    });

    // Subscribe to connection status
    this.connectionSubscription = this.wsService.connected$.subscribe((status) => {
      this.connected = status;
    });
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
    this.connectionSubscription?.unsubscribe();
  }

  getSideClass(side: string): string {
    return side === '1' ? 'buy' : 'sell';
  }

  getSideLabel(side: string): string {
    return side === '1' ? 'BUY' : 'SELL';
  }

  formatPrice(price: number): string {
    return price.toFixed(2);
  }

  formatQuantity(qty: number): string {
    return qty.toLocaleString();
  }

  getStatusClass(status: string): string {
    switch(status) {
      case 'FILLED':
        return 'status-filled';
      case 'PARTIALLY_FILLED':
        return 'status-partial';
      case 'NEW':
        return 'status-new';
      case 'REJECTED':
        return 'status-rejected';
      default:
        return 'status-unknown';
    }
  }

  clearOrders(): void {
    this.orders = [];
  }

  trackByOrderId(index: number, order: Order): string {
    return order.clOrdID;
  }
}
