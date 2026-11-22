package com.swiftdrop.pricing.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.swiftdrop.pricing.client.dto.ParcelResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class ReceiptPdfService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public byte[] generateParcelReceipt(ParcelResponseDto parcel) {
        log.info("Generating PDF receipt for parcel: {}", parcel.getTrackingNumber());

        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Color primaryBlue = new Color(30, 64, 175);
            Color lightGray = new Color(243, 244, 246);
            Color darkText = new Color(31, 41, 55);

            Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, primaryBlue);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, darkText);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, darkText);
            Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10, darkText);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

            Paragraph brand = new Paragraph("SwiftDrop Courier Logistics", brandFont);
            brand.setAlignment(Element.ALIGN_LEFT);
            document.add(brand);

            Paragraph docType = new Paragraph("OFFICIAL DELIVERY RECEIPT & TAX INVOICE", titleFont);
            docType.setSpacingBefore(4);
            document.add(docType);

            LineSeparator line = new LineSeparator(1f, 100, primaryBlue, Element.ALIGN_CENTER, -2);
            document.add(line);
            document.add(new Paragraph(" "));

            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingAfter(15);

            addCellNoBorder(metaTable, "Tracking Number: " + parcel.getTrackingNumber(), labelFont);
            addCellNoBorder(metaTable, "Date: " + (parcel.getCreatedAt() != null ? DATE_FORMATTER.format(parcel.getCreatedAt()) : "N/A"), labelFont, Element.ALIGN_RIGHT);
            addCellNoBorder(metaTable, "Payment Status: " + (parcel.getPaymentStatus() != null ? parcel.getPaymentStatus() : "PAID"), valueFont);
            addCellNoBorder(metaTable, "Delivery Status: " + (parcel.getStatus() != null ? parcel.getStatus() : "DELIVERED"), valueFont, Element.ALIGN_RIGHT);
            document.add(metaTable);

            PdfPTable partyTable = new PdfPTable(2);
            partyTable.setWidthPercentage(100);
            partyTable.setSpacingAfter(20);

            PdfPCell senderHeader = new PdfPCell(new Phrase("SENDER (DISPATCH)", labelFont));
            senderHeader.setBackgroundColor(lightGray);
            senderHeader.setPadding(6);
            partyTable.addCell(senderHeader);

            PdfPCell recipientHeader = new PdfPCell(new Phrase("RECIPIENT (DESTINATION)", labelFont));
            recipientHeader.setBackgroundColor(lightGray);
            recipientHeader.setPadding(6);
            partyTable.addCell(recipientHeader);

            String senderContent = String.format("%s\nPhone: %s\nAddress: %s\nZone: %s",
                    safeString(parcel.getSenderName()),
                    safeString(parcel.getSenderPhone()),
                    safeString(parcel.getSenderAddress()),
                    safeString(parcel.getOriginZone()));
            PdfPCell senderCell = new PdfPCell(new Phrase(senderContent, valueFont));
            senderCell.setPadding(8);
            partyTable.addCell(senderCell);

            String recipientContent = String.format("%s\nPhone: %s\nAddress: %s\nZone: %s",
                    safeString(parcel.getRecipientName()),
                    safeString(parcel.getRecipientPhone()),
                    safeString(parcel.getRecipientAddress()),
                    safeString(parcel.getDestinationZone()));
            PdfPCell recipientCell = new PdfPCell(new Phrase(recipientContent, valueFont));
            recipientCell.setPadding(8);
            partyTable.addCell(recipientCell);

            document.add(partyTable);

            PdfPTable feeTable = new PdfPTable(new float[]{3f, 1f});
            feeTable.setWidthPercentage(100);
            feeTable.setSpacingAfter(20);

            PdfPCell descHeader = new PdfPCell(new Phrase("Service Description", labelFont));
            descHeader.setBackgroundColor(lightGray);
            descHeader.setPadding(6);
            feeTable.addCell(descHeader);

            PdfPCell amountHeader = new PdfPCell(new Phrase("Amount (LKR)", labelFont));
            amountHeader.setBackgroundColor(lightGray);
            amountHeader.setHorizontalAlignment(Element.ALIGN_RIGHT);
            amountHeader.setPadding(6);
            feeTable.addCell(amountHeader);

            String serviceDesc = String.format("Parcel Delivery (%s Type) - Weight: %.2f kg",
                    safeString(parcel.getDeliveryType()),
                    parcel.getWeightKg() != null ? parcel.getWeightKg().doubleValue() : 1.0);
            PdfPCell row1 = new PdfPCell(new Phrase(serviceDesc, valueFont));
            row1.setPadding(6);
            feeTable.addCell(row1);

            PdfPCell row1Amount = new PdfPCell(new Phrase(parcel.getFinalFee() != null ? String.format("%.2f", parcel.getFinalFee()) : "0.00", valueFont));
            row1Amount.setHorizontalAlignment(Element.ALIGN_RIGHT);
            row1Amount.setPadding(6);
            feeTable.addCell(row1Amount);

            PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL PAID", labelFont));
            totalLabel.setBackgroundColor(lightGray);
            totalLabel.setPadding(8);
            feeTable.addCell(totalLabel);

            PdfPCell totalAmount = new PdfPCell(new Phrase(parcel.getFinalFee() != null ? "LKR " + String.format("%.2f", parcel.getFinalFee()) : "LKR 0.00", labelFont));
            totalAmount.setBackgroundColor(lightGray);
            totalAmount.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalAmount.setPadding(8);
            feeTable.addCell(totalAmount);

            document.add(feeTable);

            Paragraph footer = new Paragraph("Thank you for shipping with SwiftDrop Courier! " +
                    "For parcel tracking and real-time updates, visit https://swiftdrop.lk or chat with our 24/7 AI Assistant.", smallFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(30);
            document.add(footer);

            document.close();
        } catch (DocumentException ex) {
            log.error("Failed to generate PDF document", ex);
            throw new RuntimeException("Error rendering PDF receipt", ex);
        }

        return out.toByteArray();
    }

    private void addCellNoBorder(PdfPTable table, String text, Font font) {
        addCellNoBorder(table, text, font, Element.ALIGN_LEFT);
    }

    private void addCellNoBorder(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(3);
        table.addCell(cell);
    }

    private String safeString(String value) {
        return (value != null && !value.isBlank()) ? value : "N/A";
    }
}
