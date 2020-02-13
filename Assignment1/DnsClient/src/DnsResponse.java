import java.io.*;
import java.net.*;

public class DnsResponse {
	
	private byte[] responseData;
	private String queryType;
	private int querySize;
	private int ANCount, NSCount, ARCount;
	private boolean AA;
	
	public DnsResponse(byte[] responseData, int querySize, String queryType) {
		this.responseData = responseData;
		this.querySize = querySize;
		this.queryType = queryType;
		this.AA = getBit(responseData[2], 2) == 1;
        
		this.validateResponse();
		this.updateRecordCounts();
		this.printRecords();
	}	

	private void validateResponse() {
		if ((int)getBit(this.responseData[2],7) != 1) {
			throw new RuntimeException("ERROR\tReceived response is a query, not a response.");
		}
		if ((int)getBit(this.responseData[3],7) != 1) {
			throw new RuntimeException("ERROR\tServer does not support recursive queries.");
		}
		switch(getRCode(this.responseData[3])){
		case 1:
			throw new RuntimeException("ERROR\tInvalid format, the name server was unable to interpret the query.");
		case 2:
			throw new RuntimeException("ERROR\tServer failure, the name server was unable to process this query due to a problem with the name server.");
		case 3:
			throw new RuntimeException("ERROR\tName error, the domain name referenced in the query does not exist.");
		case 4:
			throw new RuntimeException("ERROR\tNot implemented, the name server does not support the requested kind of query.");
		case 5:
			throw new RuntimeException("ERROR\tRefused, the name server refuses to perform the requested operation for policy reasons.");
		default:
			break;
		}
	}
	
	private void updateRecordCounts() {
		byte[] ANCount = {this.responseData[6], this.responseData[7]};
		this.ANCount = getWord(ANCount);
		byte[] NSCount = {this.responseData[8], this.responseData[9]};
		this.NSCount = getWord(NSCount);
		byte[] ARCount = {this.responseData[8], this.responseData[9]};
		this.ARCount = getWord(ARCount);
	}
	
	private void printRecords() {
		int index = this.querySize;
		if (this.ANCount > 0) {
			System.out.println("***Answer Section (" + this.ANCount + " records)***");
			for(int i = 0; i < this.ANCount; i ++){
				index += this.printRecord();
	        }
		}
		if (this.NSCount > 0) {
			for(int i = 0; i < this.NSCount; i ++){				
	        }
		}
		if (this.ARCount > 0) {
			System.out.println("***Additional Section (" + this.ARCount + " records)***");
			for(int i = 0; i < this.ARCount; i ++){				
	        }
		}
	}
	
	private int printRecord() {
		return 0;
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
