package com.psd.smartcart_ecommerce.services;

import com.psd.smartcart_ecommerce.payload.StripeCheckoutSessionDto;
import com.psd.smartcart_ecommerce.payload.StripePaymentDto;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.model.PaymentIntent;

public interface StripeService {
    PaymentIntent paymentIntent(StripePaymentDto stripePaymentDto) throws StripeException;

    Session createCheckoutSession(StripeCheckoutSessionDto dto) throws StripeException;
}
