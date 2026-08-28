# Client side integration

## Document revision sheet

| Version | Author | Date | Description |
| --- | --- | --- | --- |
| **0.1.1** | Vladislav Popov | 20.10.2022 | Doc creation |
| **0.1.2** | Vladislav Popov | 05.01.2023 | Transaction details method updated. |
| **0.1.3** | Vladislav Popov | 28.12.2023 | Visa Secure Update required fields added to requests. |

---

## Table of contents

---

## 1. Overview

This document describes client side integration to TXPG E-Commerce module.

| Test url |  |
| --- | --- |
| Test Basic auth user | TerminalSys/Admin |
| Test Basic auth password | 1234 |

---

## 2. Authentication

All operations require authentication.

The following authentication options are supported:

HTTP Basic. In this case, the authentication token is formed from the composite login and password, separated by ":"

---

## 3. Errors

If an error occurs while processing the request, a response of the form is returned:

```json
{
  "errorCode": "error code value",

  "errorDescription": "Error Description value"
}
```

where:

| **errorCode** | **error code String value** |
| --- | --- |
| **errorDescription** | **error description String value** |

---

# 4. Transaction flow

## **4.1 NON-PSP Transaction flow (Common payment)**

1.  Send createOrder request (5.1). If success response (not error response like in point 3) Go to step **b**
2. Redirect to url with values from Create Order response (point a): Url Sample:
    
    ```jsx
    {{order.hppUrl}}/?id={{order.id}}&password={{order.password}}
    ```
    
3. Redirect client with several (described below) transaction fields to **redirect (callback ) url** after transaction completion on PC (Processing center side). Transaction flow completed.
    
    Callback url sample:
    
    ```jsx
    {{callback.url}}?ID=1234&PASSWORD=r421was123wa&STATUS=FullyPaid
    ```
    

> Note that **STATUS** parameter value can be temporary. So you have to verify transaction status using a **Transaction details** request.
> 

---

## **4.2 NON-PSP card save payment flow.**

1. Send Non-PSP card save create order request (5.2). If success response (not error response like in point 3) Go to step **b**
2. Redirect to url with values from Create Order response (point **a**): Url Sample:
    
    ```jsx
    {{order.hppUrl}}/?id={{order.id}}&password={{order.password}}
    ```
    
3. Redirect client with several (described below) transaction fields to **redirect (callback ) url** after transaction completion on PC (Processing center side). Transaction flow completed.
    
    Callback url sample:
    
    ```jsx
    {{callback.url}}?ID=1234&PASSWORD=r421was123wa&STATUS=FullyPaid&TOKENID=**1234**
    ```
    
    <aside>
    <img src="https://app.notion.com/icons/error_red.svg" alt="https://app.notion.com/icons/error_red.svg" width="40px" />
    
    Here TOKENID - is calculated token value for token payment
    
    </aside>
    

> Note that **STATUS** parameter value can be temporary. So you have to verify transaction status using a **Transaction details** request.
> 

---

## **4.3 Recurrent payment flow (no cvv2 and no 3d checks).**

1. Send recurrent payment create order request (5.3). If success response (not error response like in point 3) Go to step **b**
2. Send set-source-token request (5.4). If success response (not error response like in point 3). Go to step **c**
3. Send Execute transaction (5.5). If success response (not error response like in point 3), transaction completed.

---

## **4.4 NON-PSP P2P Transaction flow (Card to card).**

1. Send create order request (5.9). If success response (not error response like in point 3) Go to step **b**
2. Redirect to url with values from Create Order response (point a): Url Sample:
    
    ```jsx
    {{order.hppUrl}}/?id={{order.id}}&password={{order.password}}
    ```
    
3. Redirect client with several (described below) transaction fields to **redirect (callback ) url** after transaction completion on PC (Processing center side). Transaction flow completed.
    
    Callback url sample:
    
    ```jsx
    {{callback.url}}?ID=1234&PASSWORD=r421was123wa&STATUS=FullyPaid
    ```
    

> Note that **STATUS** parameter value can be temporary. So you have to verify transaction status using a **Transaction details** request.
> 

---

# **Request/Response Samples**

### **5.1 SMS common payment create order request.**

| Method | POST |
| --- | --- |
| TEST URL | https://test.millikart.az:8083 |
| URI | /order |

| Header name | Header value |
| --- | --- |
| Content-type | application/json |
| Authorization | Basic *value* |

> At least one of the following: mobilePhone, homePhone or workPhone must be provided by either the client or the merchant.
If one is included in the request, it will show up on the payment page and the client won’t be able to edit it.
If none are provided, the client will need to add one on the payment page to complete the payment.
> 

Request body

```json
{
    "order": {
        "typeRid": "Order_SMS",
        "amount": "5.00",
        "currency": "AZN",
        "description": "Test description",
        "language": "en",
        "subMerchant": {
	     "url": "http://test.com"
  },
        "ridByMerchant": "1234567890",
        "hppRedirectUrl": "http://test.com",
        "custAttrs": [
            {
                "rid": "template",
                "valAsStr": "golden_pay_lv"
            },
            {
                "rid": "F104",
                "valAsStr": "reference value"
            }	     
        ],
        "billingAddress": {
            "country": "AZE",
            "postCode": "1111",
            "regionCode": "10",
            "city": "Baku",
            "line1": "Ali mustafayev 5A"
        },
        "tdsPresetAreq": {
            "cardholderName": "Test Testov",
            "email": "test@test.az",
            "homePhone": {
                "subscriber": "120000000",
                "cc": "994"
            },
            "mobilePhone": {
                "subscriber": "700000000",
                "cc": "994"
            },
            "workPhone": {
                "subscriber": "120000000",
                "cc": "994"
            }
        }
    }
}
```

Success response body:

```json
{
  "order": {
    "hppUrl": "http://172.23.1.7:8003",
    "id": 1944,
    "status": "Preparing",
    "password": "2gszuibs780x"
  }
}
```

| Field name | Field type | Description | Mandatory | Static |
| --- | --- | --- | --- | --- |
| typeRid | String | transaction order type | true | true |
| amount | String | amount value | true | false |
| currency | String | currency short name | true | false |
| language | String | order language | true | false |
| description | String | order description | false | false |
| subMerchant
.url | String | Merchants main page url | true | true |
| hppRedirectUrl | String | callback url | true | true |
| ridByMerchant | String | unique transaction id on merchant side (optional) | false | false |
| custAttrs | List<Object> | custom attributes (optional).
If you want to see your reference number in our reports, add this object like rid=”F104” and valAsStr = *you reference number* | false | false |
| billingAddress | Object | Billing address object details | false | false |
| billingAddress.country | String | Billing address country code (ISO 3166-1 A-3) | false | false |
| billingAddress.postCode | String | Billing address postal code | false | false |
| billingAddress.regionCode | String | billing address region code | false | false |
| billingAddress.city | String | Billing address city | false | false |
| billingAddress.line1 | String | Billing address value | false | false |
| tdsPresetAreq | String | 3D Authentication required values object | true | false |
| tdsPresetAreq.cardholderName | String | Payer cardholder name | true | false |
| tdsPresetAreq.email | String | Payer email | true | false |
| tdsPresetAreq.homePhone | String | Payer home phone | true | false |
| tdsPresetAreq.mobilePhone | String | Payer mobile phone | true | false |
| tdsPresetAreq.workPhone | String | Payer work phone | true | false |
| subscriber | String | Mobile operator code + number | true | false |
| cc | String | Country mobile code | true | false |
| hppUrl | String | card data entering page | true | false |
| id | Integer | transaction order id | true | false |
| status | String | transaction status | true | false |
| password | String | order password to continue transaction | true | false |

Url Sample:

```json
{{order.hppUrl}}/?id={{order.id}}&password={{order.password}}
```

---

### **5.2 NON-PSP Payment with card save create order request**

| Method | POST |
| --- | --- |
| TEST URL | https://test.millikart.az:8083 |
| URI | /order |

| Header name | Header value |
| --- | --- |
| Content-type | application/json |
| Authorization | Basic *value* |

> At least one of the following: mobilePhone, homePhone or workPhone must be provided by either the client or the merchant.
> 

> If one is included in the request, it will show up on the payment page and the client won’t be able to edit it.
If none are provided, the client will need to add one on the payment page to complete the payment.
> 

Request Body

```json
{
    "order": {
        "typeRid": "Order_SMS",
        "amount": "5.00",
        "currency": "AZN",
        "description": "Test description",
        "language": "en",
        "subMerchant": {
	     "url": "http://test.com"
  },
        "ridByMerchant": "1234567890",
        "hppRedirectUrl": "http://test.com",
	 "hppCofCapturePurposes": [
           "Cit"
	  ],
        "custAttrs": [
            {
                "rid": "template",
                "valAsStr": "golden_pay_lv"
            },
            {
                "rid": "F104",
                "valAsStr": "reference value"
            },	     
        ],
        "billingAddress": {
            "country": "AZE",
            "postCode": "1111",
            "regionCode": "10",
            "city": "Baku",
            "line1": "Ali mustafayev 5A"
        },
        "tdsPresetAreq": {
            "cardholderName": "Test Testov",
            "email": "test@test.az",
            "homePhone": {
                "subscriber": "120000000",
                "cc": "994"
            },
            "mobilePhone": {
                "subscriber": "700000000",
                "cc": "994"
            },
            "workPhone": {
                "subscriber": "120000000",
                "cc": "994"
            }
        }
    }
}

```

Success response body:

```json
{
  "order": {
    "hppUrl": "http://172.23.1.7:8003",
    "id": 1944,
    "status": "Preparing",
    "password": "2gszuibs780x"
  }
}
```

| Field name | Field type | Description | Mandatory | Static |
| --- | --- | --- | --- | --- |
| typeRid | String | transaction order type | true | true |
| amount | String | amount value | true | false |
| currency | String | currency short name | true | false |
| language | String | order language | true | false |
| hppCofCapturePurposes | List<String> | Card storage including values | true | true |
| description | String | order description | false | false |
| subMerchant
.url | String | Merchants main page url | true | true |
| hppRedirectUrl | String | callback url | true | true |
| ridByMerchant | String | unique transaction id on merchant side (optional) | false | false |
| custAttrs | List<Object> | Optional custom attributes:
If you'd like your reference number to appear in our reports, include an object with the attribute rid=”F104” and set valAsStr = *your reference number* | false | false |
| billingAddress | Object | Billing address object details | false | false |
| billingAddress.country | String | Billing address country code (ISO 3166-1 A-3) | false | false |
| billingAddress.postCode | String | Billing address postal code | false | false |
| billingAddress.regionCode | String | billing address region code | false | false |
| billingAddress.city | String | Billing address city | false | false |
| billingAddress.line1 | String | Billing address value | false | false |
| tdsPresetAreq | String | 3D Authentication required values object | true | false |
| tdsPresetAreq.cardholderName | String | Payer cardholder name | true | false |
| tdsPresetAreq.email | String | Payer email | true | false |
| tdsPresetAreq.homePhone | String | Payer home phone | true | false |
| tdsPresetAreq.mobilePhone | String | Payer mobile phone | true | false |
| tdsPresetAreq.workPhone | String | Payer work phone | true | false |
| subscriber | String | Mobile operator code + number | true | false |
| cc | String | Country mobile code | true | false |
| hppUrl | String | card data entering page | true | false |
| id | Integer | transaction order id | true | false |
| status | String | transaction status | true | false |
| password | String | order password to continue transaction | true | false |

Url Sample:

```json
{{order.hppUrl}}/?id={{order.id}}&password={{order.password}}
```

---

### **5.3 Recurrent payment create order.**

| Method | POST |
| --- | --- |
| TEST URL | http://test.millikart.az:8000 |
| URI | /order |

| Header name | Header value |
| --- | --- |
| Content-type | application/json |
| Authorization | Basic *value* |

Request Body:

```json
{
    "order": {
        "typeRid": "Order_recurrent",
        "amount": "5.00",
        "currency": "AZN",
        "description": "Test description",
        "ridByMerchant": "123456",
        "language": "en",
        "hppCofCapturePurposes": [
            "Cit"
        ],
     custAttrs": [
					  {
							   "rid": "F104",
                 "valAsStr": "reference value"
					  }
     ]
    }
}
```

Response Body:

```json
{
  "order": {
    "hppUrl": "http://172.23.1.7:8003",
    "id": 1944,
    "status": "Preparing",
    "password": "2gszuibs780x"
  }
}
```

| Field name | Field type | Description |
| --- | --- | --- |
| typeRid | String | transaction order type |
| amount | String | amount value |
| currency | String | currency short name |
| language | String | order language |
| description | String | order description |
| ridByMerchant | String | transaction id on merchant side |
| hppUrl | String | card data entering page |
| id | Integer | transaction order id |
| status | String | transaction status |
| password | String | order password to continue transaction |
| custAttrs | List<Object> | Optional custom attributes:
If you'd like your reference number to appear in our reports, include an object with the attribute rid=”F104” and set valAsStr = *your reference number* |

### **5.4 Set src token.**

| Method | POST |
| --- | --- |
| TEST URL | http://test.millikart.az:8000 |
| URI | /order/*order.id*/set-src-token?password=*order.password* |

| Header name | Header value |
| --- | --- |
| Content-type | application/json |
| Authorization | Basic *value* |

Request body

```json
{
    "token": {
        "storedId": *token id value*
    }
}
```

Response body 

```json
{
    "order": {
        "status": "Preparing",
        "cvv2AuthStatus": "IneligibleOrder",
        "tdsV1AuthStatus": "IneligibleOrder",
        "tdsV2AuthStatus": "IneligibleOrder",
        "otpAutStatus": "NotSupportedIss",
        "srcToken": {
            "id": 3410,
            "paymentMethod": "Card",
            "role": "Src",
            "status": "Active",
            "regTime": "2022-09-21 18:01:44",
            "displayName": "401200******3337",
            "card": {
                "expiration": "0323",
                "brand": "Mastercard"
            }
        }
    }
}
```

| Field name | Field type | Field description |
| --- | --- | --- |
| token.storedId | Integer | token value |
| order.status | String | order status |
| order.cvv2AuthStatus | String | cvv 2 auth status |
| order.tdsV1AuthStatus | String | 3d v1 auth status |
| order.tdsV2AuthStatus | String | 3d v2 auth status |
| order.otpAutStatus | String | otp auth status |
| order.srcToken | Object | source token info |
| order.tdsData.aReq | Object | aReq required data from ACS |

---

### **5.5 Execute transaction.**

| Method | POST |
| --- | --- |
| TEST URL | http://test.millikart.az:8000 |
| URI | /order/*order.id*/exec-tran |
|  |  |

| Header name | Header value |
| --- | --- |
| Content-type | application/json |
| Authorization | Basic *value* |

Request body

```json
{
    "tran": {
        "phase": "Single",
        "amount": "5.00"
    }
}
```

Response body

```json
{
    "tran": {
        "approvalCode": "704731",
         "match": {
            "tranActionId": "220921-14111479-000x1d=",
            "ridByPmo": "220921512320958690"
        }
    }
}
```

| Field name | Field type | Field description |
| --- | --- | --- |
| phase | String | Single
static |
| conditions.cofCapturePurposes | List<String> | “Cit” (static value) |
| match.tranActionId | String | transaction id in e-comm module |
| match.ridByPmo | String | transaction id in core system |

---

### **5.6 Reversal**

| Method | POST |
| --- | --- |
| TEST URL | http://test.millikart.az:8000 |
| URI | /order/*order.id*/exec-tran |

| Header name | Header value |
| --- | --- |
| Content-type | application/json |
| Authorization | Basic *value* |

Request body

```json
{
    "tran": {
        "voidKind": "Full",
        "amount": "5.00",
        "phase": "Single"
    }
}
```

Response body

```json
{
    "tran": {
        "approvalCode": "340775",
        "match": {
            "tranActionId": "220613-09172925-000hbr=",
            "ridByPmo": "220613334596244733"
        }
    }
}
```

> If partial reversal - “voidKind”: “Partial”.
> 

---

### **5.7 Refund**

| Method | POST |
| --- | --- |
| TEST URL | http://test.millikart.az:8000 |
| URI | /order/*order.id*/exec-tran |

| Header name | Header value |
| --- | --- |
| Content-type | application/json |
| Authorization | Basic *value* |

Request body

```json
{
    "tran": {
        "phase": "Single",
        "amount": "1.00",
        "type": "Refund"
    }
}
```

Response body

```json
{
    "tran": {
        "approvalCode": "340775",
        "match": {
            "tranActionId": "220613-09172925-000hbr=",
            "ridByPmo": "220613334596244733"
        }
    }
}
```

---

### **5.8 Transaction details**

| Method | GET |
| --- | --- |
| TEST URL | http://test.millikart.az:8000 |
| URI | /order/{{order.id}}?password={{order.passwordvalue}} |

| Header name | Header value |
| --- | --- |
| Content-type | application/json |
| Authorization | Basic *value* |

### **5.8.1 Additional GET parameters:**

| Parameter name | Parameter value |
| --- | --- |
| orderDetailLevel | 2 |
| tokenDetailLevel | 2 |
| tranDetailLevel | 2 |

### **5.8.2 Success response example without get parameters:**

Example: 

```json
**http://{{baseUrl}}/order/{{orderId}}?password={{password}}**
```

```json
{
    "order": {
        "id": 7117,
        "typeRid": "Order_SMS",
       "ridByMerchant": "123123871283618376123",
       "prevStatus": "Preparing",
        "status": "FullyPaid",
        "lastStatusLogin": "Admin",
        "amount": 5,
        "currency": "AZN",
        "createTime": "2022-12-05 11:05:34",
        "finishTime": "2022-12-06 11:06:25",
        "type": {
            "allowVoid": false,
            "title": "Order SMS"
        }
    }
}
```

### **5.8.3 Success response with orderDetailLevel=2 parameter:**

Example:

```json
**http://{{baseUrl}}/order/{{orderId}}?password={{password}}&orderDetailLevel=2**
```

```json
{
    "order": {
        "id": 11338,
        "hppUrl": "https://test.millikart.az:8004",
        "password": "1h1pq153fk8xk",
        "status": "FullyPaid",
        "ridByMerchant": "123123871283618376123",
        "prevStatus": "Preparing",
        "lastStatusLogin": "Admin",
        "amount": 5,
        "currency": "AZN",
        "terminal": {
            "id": 1,
            "rid": "asd",
            "title": "Term",
            "mcc": 743,
            "status": "Active"
        },
        "srcAmount": 5,
        "srcAmountFull": 5,
        "srcCurrency": "AZN",
        "dstAmount": 5,
        "dstCurrency": "AZN",
        "createTime": "2023-03-14 10:31:23",
        "lastTran": {
            "approvalCode": "629677",
            "actionId": "230314-06303941-0024iz=",
            "orderId": 11338,
            "terminalId": 1,
             "rrn": "629677123123123123",
            "merchantId": 1,
            "billingStatus": "Normal",
            "isReversal": false,
            "ridByAcquirer": "230314000000002720",
            "ridByPmo": "230314000000002720",
            "regTime": "2023-03-14 10:30:39",
            "clearDay": "2020-06-18",
            "clearAmount": 5,
            "clearCcy": "AZN",
            "amount": 5,
            "currency": "AZN",
            "description": "Purchase",
            "phase": "Single",
            "type": "Purchase"
        },
        "storedTokens": [
            {
                "id": 9217
            }
        ],
        "cvv2AuthStatus": "Provided",
        "tdsV1AuthStatus": "IneligibleOrder",
        "tdsV2AuthStatus": "NotSupportedIss",
        "authorizedChargeAmount": 5,
        "clearedChargeAmount": 5,
        "clearedRefundAmount": 0,
        "description": " test order",
        "language": "en",
        "merchant": {
            "id": 1,
            "title": "Merch",
            "businessAddress": {
                "country": "RUS",
                "countryA2": "RU",
                "countryN3": 643,
                "postCode": "Postal Code",
                "regionCode": "AZ",
                "city": "BAKU",
                "streetAddress": "KARLMARKS",
                "line1": "LINE",
                "line2": "LINE2",
                "line3": "LINE3"
            }
        },
        "initiationEnvKind": "Browser",
        "type": {
            "allowVoid": true,
            "hppTranPhase": "Single",
            "secretLength": 6,
            "title": "Order_SMS",
            "rid": "Order_SMS",
            "paymentMethods": [
                "Card"
            ],
            "allowTdsAttempt": false,
            "allowTdsCant": false,
            "allowTdsChallenged": true,
            "allowTranTypes": [
                "Purchase",
                "Refund",
                "CheckToken"
            ],
            "allowTranPhases": [
                "Prepare",
                "Auth",
                "Clearing",
                "Single"
            ],
            "allowAuthKinds": [
                "Final"
            ],
            "allowCofStoreUsages": [
                "Cit",
                "PartialShipment",
                "Instalment",
                "Recurring",
                "UnspecifiedMit",
                "DelayedCharge"
            ],
            "orderClass": "Sale",
            "allowCVV2": true
        },
        "hppCofCapturePurposes": [
            "Cit"
        ],
        "custAttrs": [
            {
                "rid": "PrevStatus",
                "valAsStr": "Preparing"
            },
            {
                "rid": "PmoResultCode",
                "valAsStr": "Approved"
            }
             {
                "rid": "DeclineDescription",
                "valAsStr": "Invalid PAN"      // OBJECT EXISTS ONLY IF TRANSACTION ERROR 
            },
            {
                "rid": "PmoDeclineDescription",
                "valAsStr": "Invalid cvv2 for this card."      // OBJECT EXISTS ONLY IF TRANSACTION ERROR
            }
        ],
        "reportPubs": {}
    }
}

```

### **5.8.4 Success response with tokenDetailLevel=2 get parameter:**

Example:

```json
 **http://{{baseUrl}}/order/{{orderId}}?password={{password}}&tokenDetailLevel=2**
```

```json
{
    "order": {
        "id": 11338,
        "typeRid": "Order_SMS",
        "status": "FullyPaid",
         "ridByMerchant": "123123871283618376123",
        "prevStatus": "Preparing",
        "lastStatusLogin": "Admin",
        "amount": 5,
        "currency": "AZN",
        "createTime": "2023-03-14 10:31:23",
        "srcToken": {
            "id": 9216,
            "paymentMethod": "Card",
            "role": "Src",
            "status": "Active",
            "regTime": "2023-03-14 10:31:30",
            "entryMode": "ECommerce",
            "displayName": "426863******3689",
            "owner": {},
            "card": {
                "authentication": {
                    "needCvv2": false,
                    "needTds": false,
                       "tranId": "b69dbf41-eed1-4408-a6e2-2575e9ba849b",
                       "tdsDsTranId": "417f70c3-f337-4207-9f06-8a892175a4f8",
                       "timestamp": "2022-12-06 11:58:20",
                       "tdsProtocolVer": "2.1.0",
                       "tdsARes": "{\"threeDSServerTransID\":\"b69dbf41-eed1-4408-a6e2-2575e9ba849b\",\"acsTransID\":\"df2d4b9c-ea43-4f51-afc9-75aedcdd4bf1\",\"dsTransID\":\"417f70c3-f337-4207-9f06-8a892175a4f8\",\"messageType\":\"ARes\",\"messageVersion\":\"2.1.0\",\"messageExtension\":[{\"name\":\"MesExt1\",\"id\":\"ID1\",\"criticalityIndicator\":false,\"data\":{\"valueOne\":\"value\"}}],\"dsReferenceNumber\":\"3DS_LOA_DIS_PPFU_020100_00010\",\"acsReferenceNumber\":\"3DS_LOA_ACS_PPFU_020100_00013\",\"acsChallengeMandated\":\"N\",\"acsOperatorID\":\"acsOperatorUL\",\"acsURL\":\"https://test.millikart.az:9607/\",\"authenticationType\":\"02\",\"transStatus\":\"C\"}"
                },
                "expiration": "0131",
                "brand": "Visa",
                "issuerRid": "4268",
                "restoredFromId": 9217
            }
        },
        "type": {
            "allowVoid": false,
            "title": "Order_SMS",
            "allowCVV2": false
        }
    }
}
```

### **5.8.5 Success response with tranDetailLevel=2 get parameter:**

Example: 

```json
http://{{baseUrl}}/order/{{orderId}}?password={{password}}&tranDetailLevel=2
```

```json
{
    "order": {
        "id": 11338,
        "typeRid": "Order_SMS",
        "status": "FullyPaid",
        "ridByMerchant": "123123871283618376123",
        "lastStatusLogin": "Admin",
        "prevStatus": "Preparing",
        "amount": 5,
        "currency": "AZN",
        "createTime": "2023-03-14 10:31:23",
        "trans": [
            {
                "approvalCode": "629677",
                "actionId": "230314-06303941-0024iz=",
                "orderId": 11338,
                "terminalId": 1,
                "merchantId": 1,
                "billingStatus": "Normal",
                "isReversal": false,
                "ridByAcquirer": "230314000000002720",
                "ridByPmo": "230314000000002720",
                "regTime": "2023-03-14 10:30:39",
                "clearDay": "2020-06-18",
                "clearAmount": 5,
                "clearCcy": "AZN",
                "amount": 5,
                "rrn": "629677123123123123",
                "currency": "AZN",
                "description": "Purchase",
                "phase": "Single",
                "type": "Purchase"
            }
        ],
        "type": {
            "allowVoid": false,
            "title": "Order_SMS",
            "allowCVV2": false
        }
    }
}

```

### **5.8.6 Success response with all 3 parameters:**

Example: 

```json
http://{{baseUrl}}/order/{{orderId}}?password={{password}}&orderDetailLevel=2&tokenDetailLevel=2&tranDetailLevel=2
```

```json
{
    "order": {
        "id": 11338,
        "hppUrl": "https://test.millikart.az:8004",
        "password": "1h1pq153fk8xk",
        "status": "FullyPaid",
        "ridByMerchant": "123123871283618376123",
        "prevStatus": "Preparing",
        "lastStatusLogin": "Admin",
        "amount": 5,
        "currency": "AZN",
        "terminal": {
            "id": 1,
            "rid": "asd",
            "title": "Term",
            "mcc": 743,
            "status": "Active"
        },
        "srcAmount": 5,
        "srcAmountFull": 5,
        "srcCurrency": "AZN",
        "dstAmount": 5,
        "dstCurrency": "AZN",
        "createTime": "2023-03-14 10:31:23",
        "storedTokens": [
            {
                "id": 9217
            }
        ],
        "trans": [
            {
                "approvalCode": "629677",
                "actionId": "230314-06303941-0024iz=",
                "orderId": 11338,
                "terminalId": 1,
                "merchantId": 1,
                "billingStatus": "Normal",
                "isReversal": false,
                "ridByAcquirer": "230314000000002720",
                "ridByPmo": "230314000000002720",
                "regTime": "2023-03-14 10:30:39",
                "clearDay": "2020-06-18",
                "clearAmount": 5,
                "clearCcy": "AZN",
                "amount": 5,
                "rrn": "629677123123123123",
                "currency": "AZN",
                "description": "Purchase",
                "phase": "Single",
                "type": "Purchase"
            }
        ],
        "cvv2AuthStatus": "Provided",
        "tdsV1AuthStatus": "IneligibleOrder",
        "tdsV2AuthStatus": "NotSupportedIss",
        "authorizedChargeAmount": 5,
        "clearedChargeAmount": 5,
        "clearedRefundAmount": 0,
        "description": " test order",
        "language": "en",
        "srcToken": {
            "id": 9216,
            "paymentMethod": "Card",
            "role": "Src",
            "status": "Active",
            "regTime": "2023-03-14 10:31:30",
            "entryMode": "ECommerce",
            "displayName": "426863******3689",
            "owner": {},
            "card": {
                "authentication": {
                    "needCvv2": false,
                    "needTds": false
                },
                "expiration": "0131",
                "brand": "Visa",
                "restoredFromId": 9217,
                "issuerRid": "4268"
            }
        },
        "merchant": {
            "id": 1,
            "title": "Merch",
            "businessAddress": {
                "country": "RUS",
                "countryA2": "RU",
                "countryN3": 643,
                "postCode": "Postal Code",
                "regionCode": "AZ",
                "city": "BAKU",
                "streetAddress": "KARLMARKS",
                "line1": "LINE",
                "line2": "LINE2",
                "line3": "LINE3"
            }
        },
        "initiationEnvKind": "Browser",
        "type": {
            "allowVoid": true,
            "hppTranPhase": "Single",
            "secretLength": 6,
            "title": "Order_SMS",
            "rid": "Order_SMS",
            "paymentMethods": [
                "Card"
            ],
            "allowTdsAttempt": false,
            "allowTdsCant": false,
            "allowTdsChallenged": true,
            "allowTranTypes": [
                "Purchase",
                "Refund",
                "CheckToken"
            ],
            "allowTranPhases": [
                "Prepare",
                "Auth",
                "Clearing",
                "Single"
            ],
            "allowAuthKinds": [
                "Final"
            ],
            "allowCofStoreUsages": [
                "Cit",
                "PartialShipment",
                "Instalment",
                "Recurring",
                "UnspecifiedMit",
                "DelayedCharge"
            ],
            "orderClass": "Sale",
            "allowCVV2": true
        },
        "hppCofCapturePurposes": [
            "Cit"
        ],
        "custAttrs": [
            {
                "rid": "PrevStatus",
                "valAsStr": "Preparing"
            },
            {
                "rid": "PmoResultCode",
                "valAsStr": "Approved"
            },
            {
                "rid": "DeclineDescription",
                "valAsStr": "Invalid PAN"      // OBJECT EXISTS ONLY IF TRANSACTION ERROR 
            },
            {
                "rid": "PmoDeclineDescription",
                "valAsStr": "Invalid cvv2 for this card."      // OBJECT EXISTS ONLY IF TRANSACTION ERROR
            }
        ],
        "reportPubs": {}
    }
}
```

### **5.8.7 MAJOR RESPONSE FIELDS DESCRIPTION.**

| **Field name** | **Field type** | **Description** |
| --- | --- | --- |
| order.status | String | current status of transaction |
| order.trans | List<Object> | external information about each transaction of this purchase (purchase, reversals, refunds) |
| order.srcToken | Object | information about source card. |
| order.srcToken.card.restoredFromId | Integer | card token id.
**NOTICE: this value appears ONLY in case of transaction with almost saved card** |
| custAttrs.rid = “PrevStatus” | String | previous status of the transaction |
| custAttrs.rid = “DeclineDescription” | String | transaction decline description on e-commerce module side (can be not found) |
| custAttrs.rid = “PmoDeclineDescription” | String | transaction decline description on PC core system side (can be not found) |
| custAttr.rid = “PmoResultCode” | String | transaction decline code on PC core system side (can be not found) |
| storedTokens.id | List<Object> | card save token id.
**NOTICE: this value appears ONLY in case of `card save` transaction.** |

> To check transaction error description:
> 
> 1. Search for custAttrs.rid = “DeclineDescription”.
>     
>     If you find such an object, check the value of its “valAsStr” field — it represents the transaction decline description. If no object with this key is found, proceed to b).
>     
> 2. Search for custAttrs.rid = “PmoResultCode”.
>     
>     Also you can check custAttrs.rid = “PmoDeclineDescription” -> external information about error transaction.
>     

### **5.8.8 MAJOR STATUS FIELD VALUES DESCRIPTION.**

| **Value** | **Description** |
| --- | --- |
| FullyPaid | Successful purchase transaction |
| Rejected | Error transaction |
| Expired | Transaction timed out |
| Closed | Transaction closed. It means that you cannot send any api request on this transaction. |
| PartPaid | Transaction partially reversed or partially refunded |
| Cancelled | fully reversed |
| Refused | fully refunded |

> **If you’ve already completed a purchase transaction and want to check external information about it:**
Ensure that the order.trans array is not empty or null, and that trans.billingStatus is “Normal” . Also, check that trans.ridByPmo (the unique transaction ID from the Millikart Processing Center) is not null. If all these conditions are met, the transaction is considered successful.
> 
> 
> If a successful transaction was reversed or refunded:
> 
> in that case order.trans array would have several counts of objects (purchase info, reversal info and refund info will be located in order.trans array). check trans.description:
> 
> 1. if order.trans.description = “Purchase” - this object has information about purchase.
> 2. If order.trans.description = "Purchase - Void", the object contains information about a reversal. To confirm the reversal was successful, check that order.trans.billingStatus = "Normal" and order.trans.ridByPmo is not null. If both conditions are true, the reversal is successful.
> 3. If order.trans.description = "Refund", the object contains information about a refund. To confirm the refund was successful, check that order.trans.billingStatus = "Normal" and order.trans.ridByPmo is not null. If both conditions are true, the refund is successful.
> 
> To determine which transaction was the most recent, check the value of order.trans.regTime in each object of the array.
> 
> order.trans.clearAmount – the amount of the operation. If the amount is positive (for example, 1.0), it indicates a purchase. If the amount is negative (for example, -1.0), it indicates a reversal or refund.
> 

---

### **5.9 Create order request for card to card.**

| Method | POST |
| --- | --- |
| TEST URL: | https://test.millikart.az:8083 |
| URI | /order |

| Header name | Header value |
| --- | --- |
| Content-type | application/json |
| Authorization | Basic *value* |

> At least one of the following: mobilePhone, homePhone or workPhone must be provided by either the client or the merchant.
If one is included in the request, it will show up on the payment page and the client won’t be able to edit it.
If none are provided, the client will need to add one on the payment page to complete the payment.
> 

Request Body:

```json
{
  "order": {
    "typeRid": "Order_P2P_1",
    "amount": "5.00",
    "currency": "AZN",
    "description": "Test description",
    "language": "en",
    "subMerchant": {
      "url": "http://test.com"
    },
    "ridByMerchant": "1234567890",
    "hppRedirectUrl": "http://test.com",
    "remittanceMessage": "some remittance message",
    "custAttrs": [
      {
        "rid": "template",
        "valAsStr": "golden_pay_lv"
      },
      {
        "rid": "F104",
        "valAsStr": "reference value"
      }
    ],
    "billingAddress": {
      "country": "AZE",
      "postCode": "1111",
      "regionCode": "10",
      "city": "Baku",
      "line1": "Ali mustafayev 5A"
    },
    "tdsPresetAreq": {
      "cardholderName": "Test Testov",
      "email": "test@test.az",
      "homePhone": {
        "subscriber": "120000000",
        "cc": "994"
      },
      "mobilePhone": {
        "subscriber": "700000000",
        "cc": "994"
      },
      "workPhone": {
        "subscriber": "120000000",
        "cc": "994"
      }
    }
  }
}
```

Success response body

```json
{
  "order": {
    "id": 10180,
    "hppUrl": "https://test.millikart.az:8004",
    "password": "1ro7d35nyw1ax",
    "status": "Preparing",
    "cvv2AuthStatus": "Required",
    "secret": "451835"
  }
}
```

| Field name | Field type | Description | Mandatory | Static |
| --- | --- | --- | --- | --- |
| typeRid | String | transaction order type | true | true |
| amount | String | amount value | true | false |
| currency | String | currency short name | true | false |
| language | String | order language | true | false |
| description | String | order description | false | false |
| subMerchant
.url | String | Merchants main page url | true | true |
| hppRedirectUrl | String | callback url | true | true |
| ridByMerchant | String | unique transaction id on merchant side (optional) | false | false |
| remittanceMessage | String | remittance message | true | false |
| custAttrs | List<Object> | Optional custom attributes:
If you'd like your reference number to appear in our reports, include an object with the attribute rid=”F104” and set valAsStr = *your reference number* | false | false |
| billingAddress | Object | Billing address object details | false | false |
| billingAddress.country | String | Billing address country code (ISO 3166-1 A-3) | false | false |
| billingAddress.postCode | String | Billing address postal code | false | false |
| billingAddress.regionCode | String | billing address region code | false | false |
| billingAddress.city | String | Billing address city | false | false |
| billingAddress.line1 | String | Billing address value | false | false |
| tdsPresetAreq | String | 3D Authentication required values object | true | false |
| tdsPresetAreq.cardholderName | String | Payer cardholder name | true | false |
| tdsPresetAreq.email | String | Payer email | true | false |
| tdsPresetAreq.homePhone | String | Payer home phone | true | false |
| tdsPresetAreq.mobilePhone | String | Payer mobile phone | true | false |
| tdsPresetAreq.workPhone | String | Payer work phone | true | false |
| subscriber | String | Mobile operator code + number | true | false |
| cc | String | Country mobile code | true | false |
| hppUrl | String | card data entering page | true | false |
| id | Integer | transaction order id | true | false |
| status | String | transaction status | true | false |
| password | String | order password to continue transaction | true | false |

Url Sample:

```json
{{order.hppUrl}}/?id={{order.id}}&password={{order.password}}
```