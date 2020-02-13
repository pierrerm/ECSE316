class RequestTimeoutException extends Exception {
	// default constructor 
	RequestTimeoutException() {		
	} 
  
    // parametrized constructor 
	RequestTimeoutException(String message) {
		super(message);
	}
}