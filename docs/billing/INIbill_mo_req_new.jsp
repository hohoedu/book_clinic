<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@page import="com.inicis.std.util.SignatureUtil"%>
<%@page import="java.text.SimpleDateFormat"%>
<%@page import="java.util.Date"%>

    
<%


	Date date_now = new Date(System.currentTimeMillis());
	SimpleDateFormat fourteen_format = new SimpleDateFormat("yyyyMMddHHmmss");
	
	String mid = ""; //제공된 MID 입력
	String INILitekey = "";  //제공된 INILitekey 입력	
	String timestamp = fourteen_format.format(date_now);
	String orderId = mid + "_" + timestamp;
	
	String price = "1000";
	
	String hashData = SignatureUtil.hash(price+mid+orderId+timestamp+INILitekey, "SHA-512");
	
		
	
	// customData
	String period = "20261225";
    	
    String custom = "{\"period\":\"20261225\"}";
    String customData = custom.replace("\"", "&quot;");
    
	

%>
<!DOCTYPE html>
<html lang="ko">
<head> 
<title>INIpay DIVIDE WEB example</title> 
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
</head> 

    <head>
        <meta charset="UTF-8">
        <meta http-equiv="X-UA-Compatible" content="IE=edge">
        <meta name="viewport"
            content="width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <title>KG이니시스 결제샘플</title>
        <link rel="stylesheet" href="css/style.css">
		<link rel="stylesheet" href="css/bootstrap.min.css">
		
		<script> 
	        function on_pay() { 
	        	myform = document.mobileweb; 
	        	myform.action = "https://inilitepay.inicis.com/pay/card/billing";
	        	myform.target = "_self";
	        	myform.submit(); 
	        }
        </script> 
    </head>

    <body class="wrap">

        <!-- 본문 -->
        <main class="col-8 cont" id="bill-01">
            <!-- 페이지타이틀 -->
            <section class="mb-5">
                <div class="tit">
                    <h2>빌링(정기과금)</h2>
                    <p>KG이니시스 결제창을 호출하여 다양한 지불수단으로 안전한 결제를 제공하는 서비스</p>
                </div>
            </section>
            <!-- //페이지타이틀 -->


            <!-- 카드CONTENTS -->
            <section class="menu_cont mb-5">
                <div class="card">
                    <div class="card_tit">
                        <h3>모바일 빌링키발급</h3>
                    </div>

                    <!-- 유의사항 -->
                    <div class="card_desc">
                        <h4>※ 유의사항</h4>
                        <ul>
                            <li>테스트MID 결제시 실 승인되며, 당일 자정(24:00) 이전에 자동으로 취소처리 됩니다.</li>
							<li>가상계좌 채번 후 입금할 경우 자동환불되지 않사오니, 가맹점관리자 내 "입금통보테스트" 메뉴를 이용부탁드립니다.<br>(실 입금하신 경우 별도로 환불요청해주셔야 합니다.)</li>
							<li>국민카드 정책상 테스트 결제가 불가하여 오류가 발생될 수 있습니다. 국민, 카카오뱅크 외 다른 카드로 테스트결제 부탁드립니다.</li>
                        </ul>
                    </div>
                    <!-- //유의사항 -->


                    <form name="mobileweb" id="" method="post" class="mt-5" accept-charset="utf-8">
                        <div class="row g-3 justify-content-between" style="--bs-gutter-x:0rem;">
                        
      	                    <label class="col-10 col-sm-2 input param" style="border:none;">mid</label>
                            <label class="col-10 col-sm-9 input">
                                <input type="text" name="mid" value="<%=mid %>">
                            </label>
				    		
				    		<label class="col-10 col-sm-2 input param" style="border:none;">orderId</label>
                            <label class="col-10 col-sm-9 input">
                                <input type="text" name="orderId" value="<%=orderId %>">
                            </label>
				    
                            <label class="col-10 col-sm-2 input param" style="border:none;">price</label>
                            <label class="col-10 col-sm-9 input">
                                <input type="text" name="price" value="<%=price %>">
                            </label>
				    		
                           				    		
				    		<label class="col-10 col-sm-2 input param" style="border:none;">goodName</label>
                            <label class="col-10 col-sm-9 input">
                                <input type="text" name="goodName" value="테스트상품">
                            </label>
				    		
				    		<label class="col-10 col-sm-2 input param" style="border:none;">buyerName</label>
                            <label class="col-10 col-sm-9 input">
                                <input type="text" name="buyerName" value="홍길동">
                            </label>
                            
                            <label class="col-10 col-sm-2 input param" style="border:none;">buyerEmail</label>
                            <label class="col-10 col-sm-9 input">
                                <input type="text" name="buyerEmail" value="test@test.com">
                            </label>
                            
                            <label class="col-10 col-sm-2 input param" style="border:none;">buyerTel</label>
                            <label class="col-10 col-sm-9 input">
                                <input type="text" name="buyerTel" value="01011112222">
                            </label>
                            
                            
                            <label class="col-10 col-sm-2 input param" style="border:none;">merchantRedirectData</label>
                            <label class="col-10 col-sm-9 input">
                                <input type="text" name="merchantRedirectData" value="">
                            </label>
                            
                            

				    		<input type="hidden" name="returnUrl" value="가맹점경로/INIbill_mo_return_new.jsp">
                            <input type="hidden" name="timestamp" value="<%=timestamp %>">
                            <input type="hidden" name="clientIp" value="172.0.0.1">
                            <input type="hidden" name="url" value="https://test.com">
                            <input type="hidden" name="hashData" value="<%=hashData %>">
                            <input type="hidden" name="customData" value="<%=customData %>">
                            
							
                        </div>
                    </form>
				
				    <button onclick="on_pay()" class="btn_solid_pri col-6 mx-auto btn_lg" style="margin-top:50px">결제 요청</button>
					
                </div>
            </section>
			
        </main>
		
    </body>
</html>