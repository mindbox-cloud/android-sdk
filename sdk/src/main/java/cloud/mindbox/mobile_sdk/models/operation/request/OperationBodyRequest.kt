package cloud.mindbox.mobile_sdk.models.operation.request

import com.google.gson.annotations.SerializedName

public open class OperationBodyRequest : OperationBodyRequestBase {

    @SerializedName("customerAction")
    public val customerAction: CustomerActionRequest?

    @SerializedName("pointOfContact")
    public val pointOfContact: String?

    @SerializedName("addProductToList")
    public val addProductToList: ProductListItemRequest?

    @SerializedName("productList")
    internal val productList: Any?

    @SerializedName("segmentations")
    public val segmentations: List<SegmentationRequest>?

    @SerializedName("customer")
    public val customer: CustomerRequest?

    @SerializedName("order")
    public val order: OrderRequest?

    @SerializedName("discountCard")
    public val discountCard: DiscountCardRequest?

    @SerializedName("referencedCustomer")
    public val referencedCustomer: CustomerRequest?

    @SerializedName("removeProductFromList")
    public val removeProductFromList: ProductListItemRequest?

    @SerializedName("setProductCountInList")
    public val setProductCountInList: ProductListItemRequest?

    @SerializedName("promoCode")
    public val promoCode: PromoCodeRequest?

    @SerializedName("viewProductCategory")
    public val viewProductCategory: ViewProductCategoryRequest?

    @SerializedName("viewProduct")
    public val viewProductRequest: ViewProductRequest?

    @SerializedName("recommendation")
    public val recommendation: RecommendationRequest?

    /** Used for catalog with name productList and its type is [CatalogProductListRequest] **/
    public fun productList(): CatalogProductListRequest? = productList as? CatalogProductListRequest

    /** Used for product with name productList and its is array of [ProductListItemRequest] **/
    public fun productListItems(): List<ProductListItemRequest>? = (productList as? List<*>)?.mapNotNull { it as? ProductListItemRequest }

    public constructor(
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

    public constructor(
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

    public constructor(
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
