package com.psd.smartcart_ecommerce.services;
import com.psd.smartcart_ecommerce.exceptions.APIException;
import com.psd.smartcart_ecommerce.payload.StripeCheckoutSessionDto;
import com.psd.smartcart_ecommerce.payload.StripePaymentDto;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.CustomerSearchResult;
import com.stripe.model.checkout.Session;
import com.stripe.model.PaymentIntent;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerSearchParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class StripeServiceImpl implements StripeService {

    @Value("${stripe.secret.key}")
    private String stripeApiKey;

    @PostConstruct
    public void init(){
        Stripe.apiKey = stripeApiKey;
    }

        private void ensureStripeConfigured() {
                if (stripeApiKey == null || stripeApiKey.isBlank()) {
                        throw new APIException("Stripe is not configured on server. Set STRIPE_SECRET_KEY and restart backend.");
                }
        }

    @Override
    public PaymentIntent paymentIntent(StripePaymentDto stripePaymentDto) throws StripeException {
                ensureStripeConfigured();
        Customer customer;
        // Retrieve and check if customer exist
        CustomerSearchParams searchParams =
                CustomerSearchParams.builder()
                        .setQuery("email:'" + stripePaymentDto.getEmail() + "'")
                        .build();
        CustomerSearchResult customers = Customer.search(searchParams);
        if (customers.getData().isEmpty()) {
            // Create new customer
            CustomerCreateParams customerParams = CustomerCreateParams.builder()
                    .setEmail(stripePaymentDto.getEmail())
                    .setName(stripePaymentDto.getName())
                    .setAddress(
                            CustomerCreateParams.Address.builder()
                                    .setLine1(stripePaymentDto.getAddress().getStreet())
                                    .setCity(stripePaymentDto.getAddress().getCity())
                                    .setState(stripePaymentDto.getAddress().getState())
                                    .setPostalCode(stripePaymentDto.getAddress().getPincode())
                                    .setCountry(stripePaymentDto.getAddress().getCountry())
                                    .build()
                    )
                    .build();

            customer = Customer.create(customerParams);
        } else {
            // Fetch the customer that exist
            customer = customers.getData().get(0);
        }

        PaymentIntentCreateParams params =
                PaymentIntentCreateParams.builder()
                        .setAmount(stripePaymentDto.getAmount())
                        .setCurrency(stripePaymentDto.getCurrency())
                        .setCustomer(customer.getId())
                        .setDescription(stripePaymentDto.getDescription())
                        .setAutomaticPaymentMethods(
                                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                        .setEnabled(true)
                                        .build()
                        )
                        .build();

        return PaymentIntent.create(params);
    }

    @Override
    public Session createCheckoutSession(StripeCheckoutSessionDto dto) throws StripeException {
                ensureStripeConfigured();
                if (dto == null) {
                        throw new APIException("Stripe checkout payload is missing.");
                }
                if (dto.getAmount() == null || dto.getAmount() <= 0) {
                        throw new APIException("Stripe checkout amount must be greater than 0.");
                }
                if (dto.getSuccessUrl() == null || !dto.getSuccessUrl().startsWith("http")) {
                        throw new APIException("Stripe successUrl must be an absolute URL.");
                }
                if (dto.getCancelUrl() == null || !dto.getCancelUrl().startsWith("http")) {
                        throw new APIException("Stripe cancelUrl must be an absolute URL.");
                }

        // Retrieve and/or create customer (same approach as PaymentIntent)
        Customer customer;
        CustomerSearchParams searchParams =
                CustomerSearchParams.builder()
                        .setQuery("email:'" + dto.getEmail() + "'")
                        .build();
        CustomerSearchResult customers = Customer.search(searchParams);

        if (customers.getData().isEmpty()) {
            CustomerCreateParams customerParams = CustomerCreateParams.builder()
                    .setEmail(dto.getEmail())
                    .setName(dto.getName())
                    .build();
            customer = Customer.create(customerParams);
        } else {
            customer = customers.getData().get(0);
        }

        // Create Checkout Session
        SessionCreateParams.LineItem lineItem =
                SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(
                                SessionCreateParams.LineItem.PriceData.builder()
                                        .setCurrency(dto.getCurrency())
                                        .setUnitAmount(dto.getAmount())
                                        .setProductData(
                                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                        .setName(dto.getName())
                                                        .build()
                                        )
                                        .build()
                        )
                        .build();

        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(dto.getSuccessUrl())
                .setCancelUrl(dto.getCancelUrl())
                .setCustomer(customer.getId())
               // .setCustomerEmail(dto.getEmail())
                .addLineItem(lineItem);

        if (dto.getMetadata() != null && !dto.getMetadata().isEmpty()) {
            dto.getMetadata().forEach((key, value) -> params.putMetadata(key, value));
        }

        // Use description as metadata (Checkout Session doesn't have the same field like PaymentIntent)
        if (dto.getDescription() != null && !dto.getDescription().isBlank()) {
            params.putMetadata("description", dto.getDescription());
        }

        return Session.create(params.build());
    }

}