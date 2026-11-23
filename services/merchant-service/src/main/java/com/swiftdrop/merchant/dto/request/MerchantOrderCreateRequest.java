package com.swiftdrop.merchant.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MerchantOrderCreateRequest {

    @JsonAlias({"reference", "orderRef", "merchant_order_ref"})
    private String merchantOrderRef;

    private MerchantRecipientRequest recipient;

    @JsonAlias({"recipient_name", "receiverName"})
    private String recipientName;

    @JsonAlias({"recipient_phone", "receiverPhone"})
    private String recipientPhone;

    @JsonAlias({"pickup_address", "senderAddress"})
    private MerchantOrderAddressRequest pickupAddress;

    @JsonAlias({"pickupAddressString", "pickup_address_text"})
    private String pickupAddressText;

    @JsonAlias({"delivery_address", "destinationAddress"})
    private MerchantOrderAddressRequest deliveryAddress;

    @JsonAlias({"deliveryAddressString", "delivery_address_text"})
    private String deliveryAddressText;

    private String description;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.01", message = "Weight must be at least 0.01 kg")
    @JsonAlias({"weight", "weight_kg"})
    private BigDecimal weightKg;

    @JsonAlias({"parcelSize", "parcel_size"})
    private String size = "STANDARD";

    @JsonAlias({"declared_value", "value"})
    private BigDecimal declaredValue;

    @JsonAlias({"isFragile", "is_fragile"})
    private Boolean fragile = false;

    @JsonAlias({"instructions", "special_instructions"})
    private String specialInstructions;

    @JsonAlias({"payment_method", "paymentType"})
    private String paymentMethod = "COD";

    public MerchantOrderCreateRequest() {
    }

    public String getEffectiveRecipientName() {
        if (recipient != null && recipient.name() != null && !recipient.name().isBlank()) {
            return recipient.name().trim();
        }
        return recipientName != null ? recipientName.trim() : "Valued Customer";
    }

    public String getEffectiveRecipientPhone() {
        if (recipient != null && recipient.phone() != null && !recipient.phone().isBlank()) {
            return recipient.phone().trim();
        }
        return recipientPhone != null ? recipientPhone.trim() : "+94770000000";
    }

    public String getEffectivePickupAddress() {
        if (pickupAddress != null) {
            String formatted = pickupAddress.toFormattedString();
            if (!formatted.isBlank()) return formatted;
        }
        return pickupAddressText != null && !pickupAddressText.isBlank() ? pickupAddressText.trim() : "Merchant Warehouse";
    }

    public String getEffectiveDeliveryAddress() {
        if (deliveryAddress != null) {
            String formatted = deliveryAddress.toFormattedString();
            if (!formatted.isBlank()) return formatted;
        }
        return deliveryAddressText != null && !deliveryAddressText.isBlank() ? deliveryAddressText.trim() : "Recipient Address";
    }

    public String getEffectivePickupCity() {
        if (pickupAddress != null && pickupAddress.city() != null && !pickupAddress.city().isBlank()) {
            return pickupAddress.city().trim();
        }
        return "Colombo";
    }

    public String getEffectivePickupDistrict() {
        if (pickupAddress != null && pickupAddress.district() != null && !pickupAddress.district().isBlank()) {
            return pickupAddress.district().trim();
        }
        return "Colombo";
    }

    public String getEffectiveDeliveryCity() {
        if (deliveryAddress != null && deliveryAddress.city() != null && !deliveryAddress.city().isBlank()) {
            return deliveryAddress.city().trim();
        }
        return "Colombo";
    }

    public String getEffectiveDeliveryDistrict() {
        if (deliveryAddress != null && deliveryAddress.district() != null && !deliveryAddress.district().isBlank()) {
            return deliveryAddress.district().trim();
        }
        return "Colombo";
    }

    public String getEffectiveSize() {
        if (size != null && !size.isBlank()) {
            return size.trim().toUpperCase();
        }
        return "STANDARD";
    }

    public String getEffectivePaymentMethod() {
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            return paymentMethod.trim().toUpperCase();
        }
        return "COD";
    }

    public Boolean getEffectiveFragile() {
        return Boolean.TRUE.equals(fragile);
    }

    // Standard getters/setters
    public String getMerchantOrderRef() {
        return merchantOrderRef;
    }

    public void setMerchantOrderRef(String merchantOrderRef) {
        this.merchantOrderRef = merchantOrderRef;
    }

    public MerchantRecipientRequest getRecipient() {
        return recipient;
    }

    public void setRecipient(MerchantRecipientRequest recipient) {
        this.recipient = recipient;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public void setRecipientPhone(String recipientPhone) {
        this.recipientPhone = recipientPhone;
    }

    public MerchantOrderAddressRequest getPickupAddress() {
        return pickupAddress;
    }

    public void setPickupAddress(MerchantOrderAddressRequest pickupAddress) {
        this.pickupAddress = pickupAddress;
    }

    public String getPickupAddressText() {
        return pickupAddressText;
    }

    public void setPickupAddressText(String pickupAddressText) {
        this.pickupAddressText = pickupAddressText;
    }

    public MerchantOrderAddressRequest getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(MerchantOrderAddressRequest deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getDeliveryAddressText() {
        return deliveryAddressText;
    }

    public void setDeliveryAddressText(String deliveryAddressText) {
        this.deliveryAddressText = deliveryAddressText;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public BigDecimal getDeclaredValue() {
        return declaredValue;
    }

    public void setDeclaredValue(BigDecimal declaredValue) {
        this.declaredValue = declaredValue;
    }

    public Boolean getFragile() {
        return fragile;
    }

    public void setFragile(Boolean fragile) {
        this.fragile = fragile;
    }

    public String getSpecialInstructions() {
        return specialInstructions;
    }

    public void setSpecialInstructions(String specialInstructions) {
        this.specialInstructions = specialInstructions;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
