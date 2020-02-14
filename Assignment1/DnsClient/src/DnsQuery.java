import java.io.*;
import java.net.*;
import java.text.DecimalFormat;
import java.util.Random;

public class DnsQuery {
	
	private static final int HEADER_SIZE = 12;
	private static final int QTYPE_QCLASS_SIZE = 4;
	public String queryType = "A";
	private int timeout = 5000;
	private int maxRetries = 3;
	private int portNumber = 53;
	private byte[] serverBytes = new byte[4];
	private String name = "";
	private int qNameSize = 0;
	
	public DnsQuery(String args[]) {
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case "-t":
				int timeout = 1000 * Integer.parseInt(args[++i]);
				if (timeout > 60000) {
					throw new NumberFormatException("ERROR\tThe timeout option must be expressed in seconds, and must be at most 60 seconds.");
				} else {
					this.timeout = timeout;
				}
				break;
			case "-r":
				int maxRetries = Integer.parseInt(args[++i]);
				if (maxRetries > 100) {
					throw new NumberFormatException("ERROR\tThe maximum number of retries option must be at most 100.");
				} else if (maxRetries < 0){
					throw new NumberFormatException("ERROR\tThe maximum number of retries option must be at least 0.");
				} else {
					this.maxRetries = maxRetries;
				}
				break;
			case "-p":
				this.portNumber = Integer.parseInt(args[++i]);
				break;
			case "-mx":
				this.queryType = "MX";
				break;
			case "-ns":
				this.queryType = "NS";
				break;
			default:
				if (args[i].charAt(0) == '@') {
					String server = args[i].substring(1);
					String[] serverOctets = server.split("\\.");
					if (serverOctets.length > 4) {
						throw new NumberFormatException("ERROR\tThe IPv4 address of the DNS server must be in a.b.c.d.format.");
					} else {
						int byteNum = 0;
						for (String octet : serverOctets) {
							int octetValue = Integer.parseInt(octet);
							if (octetValue > 255 || octetValue < 0) {
								throw new NumberFormatException("ERROR\tThe IPv4 address of the DNS server must be comprised of octets of value between 0 and 255.");
							} else {
								serverBytes[byteNum++] = (byte) octetValue;
							}
						}
					}
					this.name = args[++i];
					this.updateQNameSize();
				} else {
					throw new IllegalArgumentException("ERROR\tIncorrect arguments, please use the following syntax:\njava DnsClient [-t timeout] [-r max-retries] [-p port] [-mx|-ns] @server name");
				}
				break;
			}
		}
	}
	
	public void sendQuery(int numRetries) throws Exception {
		if (numRetries > this.maxRetries) {
			
		}
		
		try {
			DatagramSocket clientSocket = new DatagramSocket();
			clientSocket.setSoTimeout(this.timeout);
			InetAddress IPAddress = InetAddress.getByAddress(this.serverBytes); 
			
			byte[] sendData = this.getRequestBytes();
	        byte[] receiveData = new byte[1024];
	        if (sendData.length > 1024) {
	        	clientSocket.close();
	        	throw new IllegalArgumentException("ERROR\tThe requested domain name is too large to be processed by a single query, please try again with a shorter name.");
	        }
	        
	        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, this.portNumber);
	        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
	        	
	        long startTime = System.nanoTime();    
	        clientSocket.send(sendPacket);
	        clientSocket.receive(receivePacket);
	        clientSocket.close();
	        double elapsedTime = ((double)(System.nanoTime() - startTime))/1000000000;
	        
	        DecimalFormat df = new DecimalFormat("##.####");
	        
	        System.out.println("Response received after " + df.format(elapsedTime) + " seconds and " + numRetries + " retries");
	        
	        validateResponseId(sendData, receiveData);
	        
	        DnsResponse response = new DnsResponse(receiveData, sendData.length, this.queryType);
	        
        } catch (SocketTimeoutException e) {
        	int retriesLeft = this.maxRetries - numRetries;
            System.out.println("ERROR\tSocket timed out, retries left: " + retriesLeft);
            if (retriesLeft > 0) {
            	System.out.println("Reattempting query...");
            	sendQuery(++numRetries);
            } else {
            	throw new RequestTimeoutException("ERROR\tMaximum number of retries " + this.maxRetries + " exceeded");
            }
        } catch (Exception e) {
        	throw e;
        }
        
	}
	
	public void printQuery() {
		System.out.println("DnsClient sending request for " + this.name);
		System.out.println("Server: " + this.formatServerAddress());
		System.out.println("Request type: " + this.queryType);
	}
	
	private byte[] getRequestBytes() {
		byte[] requestBytes = new byte[HEADER_SIZE + QTYPE_QCLASS_SIZE + this.qNameSize];
		this.setHeaderBytes(requestBytes);
		this.setQuestionBytes(requestBytes);
		return requestBytes;
	}
	
	private void updateQNameSize() {
		for (String label : this.name.split("\\.")) {
			this.qNameSize += 1 + label.length();
		}
		// the zero-length octet, representing the null label of the root
		this.qNameSize++;
	}
	
	private void setHeaderBytes(byte[] bytes) {
		byte[] randomizedId = new byte[2];
		new Random().nextBytes(randomizedId);
		System.arraycopy(randomizedId, 0, bytes, 0, randomizedId.length);
		bytes[2] = (byte)0x01;
		bytes[3] = (byte)0x00;
		bytes[4] = (byte)0x00;
		bytes[5] = (byte)0x01;
		bytes[6] = (byte)0x00;
		bytes[7] = (byte)0x00;
		bytes[8] = (byte)0x00;
		bytes[9] = (byte)0x00;
		bytes[10] = (byte)0x00;
		bytes[11] = (byte)0x00;
	}
	
	private void setQuestionBytes(byte[] bytes) {
		int byteNum = HEADER_SIZE;
		for (String label : this.name.split("\\.")) {
			bytes[byteNum++] = (byte) label.length();
			for (int charNum = 0; charNum < label.length(); charNum++) {
				bytes[byteNum++] = (byte) ((int)label.charAt(charNum));
			}
		}
		bytes[byteNum++] = (byte)0x00;
		bytes[byteNum++] = (byte)0x00;
		switch(this.queryType) {
		case "MX":
			bytes[byteNum++] = (byte)0x0f;
			break;
		case "NS":
			bytes[byteNum++] = (byte)0x02;
			break;
		default:
			bytes[byteNum++] = (byte)0x01;
			break;
		}
		bytes[byteNum++] = (byte)0x00;
		bytes[byteNum] = (byte)0x01;
	}
	
	private String formatServerAddress() {
		String serverAddress = "";
		for (int byteNum = 0; byteNum < this.serverBytes.length; byteNum++) {
			serverAddress += String.valueOf((int)this.serverBytes[byteNum]);
			if (byteNum < this.serverBytes.length -1) serverAddress += ".";
		}
		return serverAddress;
	}
	
	private static void printBytes(byte[] bytes) {
		System.out.println();
		for (byte b : bytes) {
			System.out.print((int)b + ", ");
		}
	}
	
	private static void validateResponseId(byte[] request, byte[] response) {
		byte[] responseIdBytes = {response[0], response[1]};
		byte[] requestIdBytes = {request[0], request[1]};
		int responseID = getWord(responseIdBytes);
		int requestID = getWord(requestIdBytes);
		if (responseID != requestID) {
			throw new RuntimeException("ERROR\tReceived response ID (" + responseID + ") does not match the Request ID (" + requestID + ").");
		}
	}
	
	private static int getWord(byte[] bytes) {
		return ((bytes[0] & 0xff) << 8) + (bytes[1] & 0xff);
	}
	
	private static int getBit(byte b, int p) {
		return (b >> p) & 1;
	}
	
	private static int getRCode(byte b) {
		return ((b >> 0) & 1) + ((b >> 1) & 1) * 2 +((b >> 2) & 1) * 4 + ((b >> 3) & 1) * 8;
	}
}
