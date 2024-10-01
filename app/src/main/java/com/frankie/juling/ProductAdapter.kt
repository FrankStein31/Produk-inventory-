package com.frankie.juling

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide

class ProductAdapter(context: Context, private val products: List<Product>) : ArrayAdapter<Product>(context, 0, products) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var itemView = convertView
        if (itemView == null) {
            itemView = LayoutInflater.from(context).inflate(R.layout.list_item_product, parent, false)
        }

        val product = products[position]

        val imageView = itemView?.findViewById<ImageView>(R.id.productImage)
        val codeView = itemView?.findViewById<TextView>(R.id.productCode)
        val nameView = itemView?.findViewById<TextView>(R.id.productName)
        val priceView = itemView?.findViewById<TextView>(R.id.productPrice)

        imageView?.let { Glide.with(context).load(product.imageUrl).into(it) }
        codeView?.text = product.code
        nameView?.text = product.name
        priceView?.text = "Rp ${product.price}"

        return itemView!!
    }
}