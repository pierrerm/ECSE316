
public class DnsDataEntry {
	private int numBytes;
	private String domainName;
	
	public int getBytes() {
		return numBytes;
	}

	public String getDomain() {
		return domainName;
	}
	
	public void setBytes(int numBytes) {
		this.numBytes = numBytes;
	}
	
	public void setDomain(String domainName) {
		this.domainName = domainName;
	}
}