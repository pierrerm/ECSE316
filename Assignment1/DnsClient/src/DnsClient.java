
public class DnsClient {

	public static void main(String[] args) throws Exception {
		try {
			DnsQuery query = new DnsQuery(args);
			query.printQuery();
			query.sendQuery(0);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
}
