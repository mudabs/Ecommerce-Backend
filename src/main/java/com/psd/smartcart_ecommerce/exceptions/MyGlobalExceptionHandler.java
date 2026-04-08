package com.psd.smartcart_ecommerce.exceptions;
import com.psd.smartcart_ecommerce.payload.APIResponse;
import com.stripe.exception.StripeException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class MyGlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> myMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        Map<String, String> response = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(err -> {
            String fieldName = ((FieldError) err).getField();
            String message = err.getDefaultMessage();
            response.put(fieldName, message);
        });
        return new ResponseEntity<Map<String, String>>(response,
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<APIResponse> myResourceNotFoundException(ResourceNotFoundException e) {
        String message = e.getMessage();
        APIResponse apiResponse = new APIResponse();
        apiResponse.setMessage(message);
        apiResponse.setStatus(false);
        return new ResponseEntity<>(apiResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(APIException.class)
    public ResponseEntity<APIResponse> myAPIException(APIException e) {
        String message = e.getMessage();
        APIResponse apiResponse = new APIResponse();
        apiResponse.setMessage(message);
        apiResponse.setStatus(false);
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<APIResponse> myConstraintViolationException(ConstraintViolationException e) {
        String message = e.getMessage();
        APIResponse apiResponse = new APIResponse();
        apiResponse.setMessage(message);
        apiResponse.setStatus(false);
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<APIResponse> myDataIntegrityViolationException(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause() != null
                ? e.getMostSpecificCause().getMessage()
                : e.getMessage();
        APIResponse apiResponse = new APIResponse();
        apiResponse.setMessage(message);
        apiResponse.setStatus(false);
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(StripeException.class)
    public ResponseEntity<APIResponse> myStripeException(StripeException e) {
        APIResponse apiResponse = new APIResponse();
        apiResponse.setMessage(e.getMessage());
        apiResponse.setStatus(false);
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<APIResponse> myHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        String message = e.getMostSpecificCause() != null
                ? e.getMostSpecificCause().getMessage()
                : e.getMessage();
        APIResponse apiResponse = new APIResponse();
        apiResponse.setMessage("Malformed JSON request body: " + message);
        apiResponse.setStatus(false);
        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }
}
