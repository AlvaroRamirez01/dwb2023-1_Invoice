package com.invoice.api.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.invoice.api.dto.ApiResponse;
import com.invoice.api.dto.DtoCustomer;
import com.invoice.api.dto.DtoProduct;
import com.invoice.api.entity.Cart;
import com.invoice.api.repository.RepoCart;
import com.invoice.configuration.client.CustomerClient;
import com.invoice.configuration.client.ProductClient;
import com.invoice.exception.ApiException;

@Service
public class SvcCartImp implements SvcCart {

	@Autowired
	RepoCart repo;
	
	@Autowired
	CustomerClient customerCl;
	
	@Autowired
	ProductClient productCl;
	
	@Override
	public List<Cart> getCart(String rfc) {
		return repo.findByRfcAndStatus(rfc,1);
	}

	@Override
	public ApiResponse addToCart(Cart cart) {
		if(!validateCustomer(cart.getRfc()))
			throw new ApiException(HttpStatus.BAD_REQUEST, "customer does not exist");
			
		/*
		 * Requerimiento 3
		 * Validar que el GTIN exista. Si existe, asignar el stock del producto a la variable product_stock 
		 */
		if (validateProduct(cart.getGtin())) {
			Integer product_stock = getStockProduct(cart.getGtin()); // cambiar el valor de cero por el stock del producto recuperado de la API Product 
			if(cart.getQuantity() > product_stock) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "invalid quantity");
			}
		}
		/*
		 * Requerimiento 4
		 * Validar si el producto ya había sido agregado al carrito para solo actualizar su cantidad
		 */
		String rfc = cart.getRfc();
		String gtin = cart.getGtin();
		Integer product_stock = getStockProduct(cart.getGtin());
		Integer cartQuantity = cart.getQuantity();
		String response = "item added";
		List<Cart> carts = repo.findByRfcAndGtin(rfc, gtin);

		if (!carts.isEmpty()) {
			Cart oldCart = carts.get(0);
			int newQuantity = oldCart.getStatus() == 0 ? cartQuantity : oldCart.getQuantity() + cartQuantity;

			if (newQuantity > product_stock) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "invalid quantity");
			} else {
				oldCart.setQuantity(newQuantity);
				cart = oldCart;
				if (oldCart.getStatus() == 1) {
					response = "quantity updated";
				}
			}
		}
		
		cart.setStatus(1);
		repo.save(cart);
		return new ApiResponse("item added");
	}

	@Override
	public ApiResponse removeFromCart(Integer cart_id) {
		if (repo.removeFromCart(cart_id) > 0)
			return new ApiResponse("item removed");
		else
			throw new ApiException(HttpStatus.BAD_REQUEST, "item cannot be removed");
	}

	@Override
	public ApiResponse clearCart(String rfc) {
		if (repo.clearCart(rfc) > 0)
			return new ApiResponse("cart removed");
		else
			throw new ApiException(HttpStatus.BAD_REQUEST, "cart cannot be removed");
	}
	
	private boolean validateCustomer(String rfc) {
		try {
			ResponseEntity<DtoCustomer> response = customerCl.getCustomer(rfc);
			if(response.getStatusCode() == HttpStatus.OK)
				return true;
			else
				return false;
		}catch(Exception e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "unable to retrieve customer information");
		}
	}

//metodo agregado	
	private boolean validateProduct(String gtin) {
		try {
			ResponseEntity<DtoProduct> response = productCl.readProduct(gtin);
			if(response.getStatusCode() == HttpStatus.OK)
				return true;
			else
				return false;
		}catch(Exception e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "product does not exist");
		}
	}
	
//metodo agregado
	private int getStockProduct(String gtin){
		try {
			DtoProduct response = productCl.readProduct(gtin).getBody();
			int stock = response.getStock();
			return stock;
		}catch(Exception e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "unable to retrieve product information");
		}
	}
	
}
