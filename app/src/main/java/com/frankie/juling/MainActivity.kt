package com.frankie.juling

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var storage: FirebaseStorage
    private lateinit var productListView: ListView
    private lateinit var productList: MutableList<Product>
    private lateinit var adapter: ProductAdapter
    private var selectedImageUri: Uri? = null

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = FirebaseDatabase.getInstance().reference.child("products")
        storage = FirebaseStorage.getInstance()
        productListView = findViewById(R.id.productListView)
        productList = mutableListOf()
        adapter = ProductAdapter(this, productList)
        productListView.adapter = adapter

        val addProductFab: FloatingActionButton = findViewById(R.id.addProductFab)
//        addProductFab.setOnClickListener { showAddProductDialog() }
        addProductFab.setOnClickListener {
            selectedImageUri = null // Clear the previously selected image
            showAddProductDialog()
        }

        loadProducts()

        productListView.setOnItemClickListener { _, _, position, _ ->
            val product = productList[position]
            showProductOptions(product)
        }
    }

    private fun loadProducts() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                productList.clear()
                for (productSnapshot in snapshot.children) {
                    val product = productSnapshot.getValue(Product::class.java)
                    product?.let { productList.add(it) }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Gagal memuat produk.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showAddProductDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Tambah Produk Baru")

        // Inflate layout dialog sebelum inisialisasi elemen UI
        val input = layoutInflater.inflate(R.layout.dialog_add_product, null)
        builder.setView(input)

        val uploadImageButton = input.findViewById<FloatingActionButton>(R.id.uploadImageButton)
        val productImageView = input.findViewById<ImageView>(R.id.productImageView)

        // Set ikon default saat dialog dibuka
        productImageView.setImageResource(R.drawable.ic_upload)

        uploadImageButton.setOnClickListener {
            openImageChooser()
        }

        // Cek apakah sudah ada gambar yang dipilih, jika iya tampilkan
        selectedImageUri?.let {
            productImageView.setImageURI(it)
        }

        builder.setPositiveButton("Add") { _, _ ->
            val name = input.findViewById<EditText>(R.id.productName).text.toString()
            val price = input.findViewById<EditText>(R.id.productPrice).text.toString().toDoubleOrNull() ?: 0.0
            val description = input.findViewById<EditText>(R.id.productDescription).text.toString()

            generateProductCode { code ->
                val newProduct = Product(UUID.randomUUID().toString(), code, name, price, description)
                addProduct(newProduct)
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun generateProductCode(callback: (String) -> Unit) {
        database.orderByChild("code").limitToLast(1).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var lastCode = 0
                for (childSnapshot in snapshot.children) {
                    val product = childSnapshot.getValue(Product::class.java)
                    product?.let {
                        lastCode = it.code.removePrefix("P").toIntOrNull() ?: 0
                    }
                }
                val newCode = "P${String.format("%04d", lastCode + 1)}"
                callback(newCode)
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Gagal generate kode produk.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun addProduct(product: Product) {
        database.child(product.id).setValue(product)
            .addOnSuccessListener {
                uploadImage(product.id)
                Toast.makeText(this, "Produk berhasil ditambah", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal menambah produk", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showProductOptions(product: Product) {
        val options = arrayOf("Update", "Delete")
        AlertDialog.Builder(this)
            .setTitle("Product Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showUpdateProductDialog(product)
                    1 -> deleteProduct(product)
                }
            }
            .show()
    }

    private fun showUpdateProductDialog(product: Product) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Update Product")

        val input = layoutInflater.inflate(R.layout.dialog_add_product, null)
        input.findViewById<EditText>(R.id.productName).setText(product.name)
        input.findViewById<EditText>(R.id.productPrice).setText(product.price.toString())
        input.findViewById<EditText>(R.id.productDescription).setText(product.description)

        val productImageView = input.findViewById<ImageView>(R.id.productImageView)
        val uploadImageButton = input.findViewById<FloatingActionButton>(R.id.uploadImageButton)

        // Load existing image or show newly selected image
        if (selectedImageUri != null) {
            productImageView.setImageURI(selectedImageUri)
        } else {
            Glide.with(this).load(product.imageUrl).into(productImageView)
        }

        uploadImageButton.setOnClickListener {
            openImageChooser()
        }

        builder.setView(input)

        builder.setPositiveButton("Update") { _, _ ->
            val updatedProduct = product.copy(
                name = input.findViewById<EditText>(R.id.productName).text.toString(),
                price = input.findViewById<EditText>(R.id.productPrice).text.toString().toDoubleOrNull() ?: 0.0,
                description = input.findViewById<EditText>(R.id.productDescription).text.toString()
            )
            updateProduct(updatedProduct)
        }

        builder.setNegativeButton("Batal") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun updateProduct(product: Product) {
        database.child(product.id).setValue(product)
            .addOnSuccessListener {
                uploadImage(product.id)
                Toast.makeText(this, "Produk berhasil diperbaharui", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memperbaharui produk", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteProduct(product: Product) {
        database.child(product.id).removeValue()
            .addOnSuccessListener {
                deleteImage(product.id)
                Toast.makeText(this, "Produk berhasil dihapus", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal menghapus produk", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openImageChooser() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            selectedImageUri = data.data
        }
    }

    private fun uploadImage(productId: String) {
        selectedImageUri?.let { uri ->
            val ref = storage.reference.child("product_images/$productId.jpg")
            ref.putFile(uri)
                .addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { downloadUri ->
                        updateProductImageUrl(productId, downloadUri.toString())
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Gagal mengupload gambar", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateProductImageUrl(productId: String, imageUrl: String) {
        database.child(productId).child("imageUrl").setValue(imageUrl)
    }

    private fun deleteImage(productId: String) {
        val ref = storage.reference.child("product_images/$productId.jpg")
        ref.delete().addOnFailureListener {
            Toast.makeText(this, "Gagal menghapus gambar", Toast.LENGTH_SHORT).show()
        }
    }
}

data class Product(
    val id: String = "",
    val code: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val description: String = "",
    val imageUrl: String = ""
) {
    // This is needed for Firebase to properly deserialize the object
    constructor() : this("", "", "", 0.0, "", "")
}