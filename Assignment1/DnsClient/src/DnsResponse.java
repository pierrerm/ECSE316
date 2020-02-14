import java.io.*;
import java.net.*;

public class DnsResponse {
	
	private byte[] responseData;
	private String queryType;
	private int querySize;
	private int ANCount, NSCount, ARCount;
	private boolean AA;
	private int index;
	
	public DnsResponse(byte[] responseData, int querySize, String queryType) {
		this.responseData = responseData;
		this.querySize = querySize;
		this.index = querySize;
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
		if (this.ANCount > 0) {
			System.out.println("***Answer Section (" + this.ANCount + " records)***");
			for(int i = 0; i < this.ANCount; i ++){
				this.printRecord(this.index, true);
	        }
		}
		if (this.NSCount > 0) {
			for(int i = 0; i < this.NSCount; i ++){	
				this.printRecord(this.index, false);			
	        }
		}
		if (this.ARCount > 0) {
			System.out.println("***Additional Section (" + this.ARCount + " records)***");
			for(int i = 0; i < this.ARCount; i ++){	
				this.printRecord(this.index, true);			
	        }
		}
	}
	
	private void printRecord(int index, boolean print) {
		DnsDataEntry entry = this.parseDomain(this.index);
		this.index = entry.getBytes();
		long cacheSeconds = 0;
		int rdLength = 0;
		switch(this.parseType()){
		case 1:
			this.validateClassCode();
			cacheSeconds = this.getCacheSeconds();
			rdLength = this.getRdLength();
			DnsDataEntry ipEntry = this.parseIp(this.index, rdLength);
			if (print) System.out.print("IP\t" + ipEntry.getDomain() + "\t" + cacheSeconds + "\t" + (this.AA ? "auth" : "nonauth") + "\n");
			this.index = ipEntry.getBytes();
			break;
		case 2:
			this.validateClassCode();
			cacheSeconds = this.getCacheSeconds();
			rdLength = this.getRdLength();
			DnsDataEntry nsEntry = parseDomain(this.index);
			if (print) System.out.print("NS\t" + entry.getDomain() + "\t" + cacheSeconds + "\t" + (this.AA ? "auth" : "nonauth") + "\n");
			this.index = nsEntry.getBytes();
			break;
		case 5:
			this.validateClassCode();
			cacheSeconds = this.getCacheSeconds();
			rdLength = this.getRdLength();
			DnsDataEntry cNameEntry = parseDomain(this.index);
			if (print) System.out.print("CNAME\t" + entry.getDomain() + "\t" + cacheSeconds + "\t" + (this.AA ? "auth" : "nonauth") + "\n");
			this.index = cNameEntry.getBytes();
			break;
		case 15:
			this.validateClassCode();
			cacheSeconds = this.getCacheSeconds();
			rdLength = this.getRdLength();
			int pref = this.getPref();
			DnsDataEntry mxEntry = parseDomain(this.index);
			if (print) System.out.print("CNAME\t" + entry.getDomain() +  "\t" + pref + "\t" + cacheSeconds + "\t" + (this.AA ? "auth" : "nonauth") + "\n");
			this.index = mxEntry.getBytes();
			break;
		default:
			throw new RuntimeException("ERROR\tUnexpected record type, could not process the server response.");
		}
	}
	
	private DnsDataEntry parseDomain(int index) {
		DnsDataEntry entry = new DnsDataEntry();
		String domain = "";
		int storedIndex = index;
		int length = -1;
		boolean compressed = false;
		
		while(this.responseData[index] != 0x00) {
			if((this.responseData[index] & 0xC0) == 0xC0 && length <= 0) {
				byte[] domainIndex = {(byte) (this.responseData[index++] & 0x3f), this.responseData[index]};
				storedIndex = index;
				compressed = true;
				index = getWord(domainIndex);
			} else {
				if (length == 0) {
					domain += ".";
					length = this.responseData[index];
				} else if (length < 0) {
					length = this.responseData[index];
				} else {
					domain += ((char) (this.responseData[index] & 0xFF));
					length--;
				}
				index++;
			} 
		}
		if (compressed) entry.setBytes(++storedIndex);
		else entry.setBytes(index);
		entry.setDomain(domain);
		return entry;
	}
	
	private DnsDataEntry parseIp(int index, int length) {
		DnsDataEntry entry = new DnsDataEntry();
		String ip = "";
		int storedIndex = index;
		while(length > 0) {
			ip += String.valueOf(this.responseData[index] & 0xff);
			length--;
			if (length != 0) {
				ip += ".";
			}
			index++; 
		}
		entry.setBytes(++storedIndex);
		entry.setDomain(ip);
		return entry;
	}
	
	private int parseType() {
		byte[] type = {this.responseData[this.index++], this.responseData[this.index++]};
		return getWord(type);
	}
	
	private void validateClassCode() {
		byte[] classCode = {this.responseData[this.index++], this.responseData[this.index++]};
		if (getWord(classCode) != 1) {
			throw new RuntimeException("ERROR\tUnexpected class code, could not process the server response.");
		}
	}
	
	private long getCacheSeconds() {
		byte[] LMB = {this.responseData[this.index++], this.responseData[this.index++]};
		byte[] RMB = {this.responseData[this.index++], this.responseData[this.index++]};
		return getWord(LMB) * 65536 + getWord(RMB);
	}
	
	private int getRdLength() {
		byte[] rdLength = {this.responseData[this.index++], this.responseData[this.index++]};
		return getWord(rdLength);
	}
	
	private int getPref() {
		byte[] pref = {this.responseData[this.index++], this.responseData[this.index++]};
		return getWord(pref);
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
