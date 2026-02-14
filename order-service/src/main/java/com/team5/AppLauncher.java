package com.team5;

import quickfix.DefaultMessageFactory;
import quickfix.FileStoreFactory;
import quickfix.ScreenLogFactory;
import quickfix.SessionSettings;
import quickfix.SocketAcceptor;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
public class AppLauncher {
 public static void main(String[] args) {
 try {
 Path settingsPath = resolveSettingsPath();
 SessionSettings settings = new SessionSettings(settingsPath.toString());
 
 // Start WebSocket server for UI
 OrderBroadcaster broadcaster = new OrderBroadcaster(8080);
 broadcaster.start();
 
 OrderApplication application = new OrderApplication(broadcaster);
 FileStoreFactory storeFactory = new FileStoreFactory(settings);
 ScreenLogFactory logFactory = new ScreenLogFactory(settings);
 DefaultMessageFactory messageFactory = new DefaultMessageFactory();
 SocketAcceptor acceptor = new SocketAcceptor(application, storeFactory, settings,
logFactory, messageFactory);
 acceptor.start();
 System.out.println("Order Service started. Listening on port 9876...");

 // Keep the process running
 System.in.read();
 acceptor.stop();
 broadcaster.stop();
 } catch (Exception e) {
 e.printStackTrace();
 }
 }

 private static Path resolveSettingsPath() throws FileNotFoundException {
 Path directPath = Paths.get("order-service.cfg");
 if (Files.exists(directPath)) {
 return directPath;
 }

 Path repoRootPath = Paths.get("order-service", "order-service.cfg");
 if (Files.exists(repoRootPath)) {
 return repoRootPath;
 }

 throw new FileNotFoundException("Unable to locate order-service.cfg in the current directory or ./order-service");
 }
}