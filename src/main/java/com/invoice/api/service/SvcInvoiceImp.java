package com.invoice.api.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.invoice.api.dto.ApiResponse;
import com.invoice.api.dto.DtoProduct;
import com.invoice.api.entity.Cart;
import com.invoice.api.entity.Invoice;
import com.invoice.api.entity.Item;
import com.invoice.api.repository.RepoCart;
import com.invoice.api.repository.RepoInvoice;
import com.invoice.api.repository.RepoItem;
import com.invoice.configuration.client.ProductClient;
import com.invoice.exception.ApiException;

@Service
public class SvcInvoiceImp implements SvcInvoice {

	@Autowired
	RepoInvoice repo;
	
	@Autowired
	RepoItem repoItem;
	
	@Autowired
	RepoCart repoCart;
	
	@Autowired
	ProductClient productCl;
	
	@Override
	public List<Invoice> getInvoices(String rfc) {
		return repo.findByRfcAndStatus(rfc, 1);
	}

	@Override
	public List<Item> getInvoiceItems(Integer invoice_id) {
		return repoItem.getInvoiceItems(invoice_id);
	}

	@Override
	public ApiResponse generateInvoice(String rfc) {
		List<Cart> listaCarrito =  repoCart.findByRfcAndStatus(rfc,1);
		if(listaCarrito.isEmpty()){
			throw new ApiException(HttpStatus.NOT_FOUND,"cart has no items");
		}
		
		Invoice invoice = new Invoice();
		invoice.setRfc(rfc);
		invoice.setStatus(1);
		invoice.setSubtotal(0.0);
		invoice.setTaxes(0.0);
		invoice.setTotal(0.0);
		invoice.setCreated_at(LocalDateTime.now());
		repo.save(invoice);
		
		
		for(Cart articulo:listaCarrito) {
			Item item = new Item();
			double price = getPriceProduct(articulo.getGtin());
			int quantity = articulo.getQuantity();
			double totalArticulo = price * quantity;
			double taxesArticulo = totalArticulo*.16;
			double subtotalArticulo = totalArticulo - taxesArticulo;
			item.setId_invoice(invoice.getInvoice_id());	
			item.setGtin(articulo.getGtin());
			item.setQuantity(quantity);
			item.setUnit_price(price);
			item.setTotal(totalArticulo);
			item.setTaxes(taxesArticulo);
			item.setSubtotal(subtotalArticulo);
			item.setStatus(1);
			setStockProduct(quantity,articulo.getGtin());
			repoItem.save(item);
		}
		
		List<Item> listItems = repoItem.getInvoiceItems(invoice.getInvoice_id());
		
		double total = 0;
		double taxes = 0;
		double subtotal = 0;
		
		for(Item items : listItems) {
			total += items.getTotal();
			taxes += items.getTaxes() ;
			subtotal += items.getSubtotal();
		}
		repo.updateInvoice(invoice.getInvoice_id(),total,taxes, subtotal);
		
		repoCart.clearCart(rfc);
		
		return new ApiResponse("invoice generated");
	}
	
	private double getPriceProduct(String gtin){
		double price = 0;
		try {
			DtoProduct response = productCl.readProduct(gtin).getBody();
			price = response.getPrice();
			return price;
		}catch(Exception e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "unable to retrieve product information");
		}
	}
	
	private void setStockProduct(int quantity, String gtin){
		try {
			DtoProduct response = productCl.readProduct(gtin).getBody();
			int stock = response.getStock() - quantity;
			productCl.updateProductStock(gtin, stock);
		}catch(Exception e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "unable to retrieve product information");
		}
	}

}
