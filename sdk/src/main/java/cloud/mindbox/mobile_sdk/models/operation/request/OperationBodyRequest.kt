package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

open class OperationBodyRequest : OperationBodyRequestBase {

    @SerializedName("customerAction")
    val customerAction: CustomerActionRequest?

    @SerializedName("pointOfContact")
    val pointOfContact: String?

    @SerializedName("addProductToList")
    val addProductToList: ProductListItemRequest?

    @SerializedName("productList")
    internal val productList: Any?

    @SerializedName("segmentations")
    val segmentations: List<SegmentationRequest>?

    @SerializedName("customer")
    val customer: CustomerRequest?

    @SerializedName("order")
    val order: OrderRequest?

    @SerializedName("discountCard")
    val discountCard: DiscountCardRequest?

    @SerializedName("referencedCustomer")
    val referencedCustomer: CustomerRequest?

    @SerializedName("removeProductFromList")
    val removeProductFromList: ProductListItemRequest?

    @SerializedName("setProductCountInList")
    val setProductCountInList: ProductListItemRequest?

    @SerializedName("promoCode")
    val promoCode: PromoCodeRequest?

    @SerializedName("viewProductCategory")
    val viewProductCategory: ViewProductCategoryRequest?

    @SerializedName("viewProduct")
    val viewProductRequest: ViewProductRequest?

    @SerializedName("recommendation")
    val recommendation: RecommendationRequest?

    /** Used for catalog with name productList and its type is [CatalogProductListRequest] **/
    fun productList(): CatalogProductListRequest? = productList as? CatalogProductListRequest

    /** Used for product with name productList and its is array of [ProductListItemRequest] **/
    fun productListItems() = (productList as? List<*>)?.mapNotNull { it as? ProductListItemRequest }

    constructor(
        customerAction: CustomerActionRequest? = null,
        pointOfContact: String? = null,
        addProductToList: ProductListItemRequest? = null,
        productList: CatalogProductListRequest? = null,
        segmentations: List<SegmentationRequest>? = null,
        customer: CustomerRequest? = null,
        order: OrderRequest? = null,
        discountCard: DiscountCardRequest? = null,
        referencedCustomer: CustomerRequest? = null,
        removeProductFromList: ProductListItemRequest? = null,
        setProductCountInList: ProductListItemRequest? = null,
        promoCode: PromoCodeRequest? = null,
        viewProductCategory: ViewProductCategoryRequest? = null,
        viewProductRequest: ViewProductRequest? = null,
        recommendation: RecommendationRequest? = null
    ) : super() {
        this.customerAction = customerAction
        this.pointOfContact = pointOfContact
        this.addProductToList = addProductToList
        this.productList = productList
        this.segmentations = segmentations
        this.customer = customer
        this.order = order
        this.discountCard = discountCard
        this.referencedCustomer = referencedCustomer
        this.removeProductFromList = removeProductFromList
        this.setProductCountInList = setProductCountInList
        this.promoCode = promoCode
        this.viewProductCategory = viewProductCategory
        this.viewProductRequest = viewProductRequest
        this.recommendation = recommendation
    }

    constructor(
        customerAction: CustomerActionRequest? = null,
        pointOfContact: String? = null,
        addProductToList: ProductListItemRequest? = null,
        segmentations: List<SegmentationRequest>? = null,
        customer: CustomerRequest? = null,
        order: OrderRequest? = null,
        discountCard: DiscountCardRequest? = null,
        referencedCustomer: CustomerRequest? = null,
        removeProductFromList: ProductListItemRequest? = null,
        setProductCountInList: ProductListItemRequest? = null,
        productList: List<ProductListItemRequest>? = null,
        promoCode: PromoCodeRequest? = null,
        viewProductCategory: ViewProductCategoryRequest? = null,
        viewProductRequest: ViewProductRequest? = null,
        recommendation: RecommendationRequest? = null
    ) : super() {
        this.customerAction = customerAction
        this.pointOfContact = pointOfContact
        this.addProductToList = addProductToList
        this.productList = productList
        this.segmentations = segmentations
        this.customer = customer
        this.order = order
        this.discountCard = discountCard
        this.referencedCustomer = referencedCustomer
        this.removeProductFromList = removeProductFromList
        this.setProductCountInList = setProductCountInList
        this.promoCode = promoCode
        this.viewProductCategory = viewProductCategory
        this.viewProductRequest = viewProductRequest
        this.recommendation = recommendation
    }

    constructor(
        customerAction: CustomerActionRequest? = null,
        pointOfContact: String? = null,
        addProductToList: ProductListItemRequest? = null,
        segmentations: List<SegmentationRequest>? = null,
        customer: CustomerRequest? = null,
        order: OrderRequest? = null,
        discountCard: DiscountCardRequest? = null,
        referencedCustomer: CustomerRequest? = null,
        removeProductFromList: ProductListItemRequest? = null,
        setProductCountInList: ProductListItemRequest? = null,
        promoCode: PromoCodeRequest? = null,
        viewProductCategory: ViewProductCategoryRequest? = null,
        viewProductRequest: ViewProductRequest? = null,
        recommendation: RecommendationRequest? = null
    ) : super() {
        this.customerAction = customerAction
        this.pointOfContact = pointOfContact
        this.addProductToList = addProductToList
        this.productList = null
        this.segmentations = segmentations
        this.customer = customer
        this.order = order
        this.discountCard = discountCard
        this.referencedCustomer = referencedCustomer
        this.removeProductFromList = removeProductFromList
        this.setProductCountInList = setProductCountInList
        this.promoCode = promoCode
        this.viewProductCategory = viewProductCategory
        this.viewProductRequest = viewProductRequest
        this.recommendation = recommendation
    }
}
