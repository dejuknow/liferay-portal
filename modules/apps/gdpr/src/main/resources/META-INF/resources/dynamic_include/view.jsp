<%@ include file="init.jsp" %>

<meta content="width=device-width, initial-scale=1" name="viewport">
<link href="<%= request.getContextPath() %>/css/custom.css" rel="stylesheet" />

<div class="cookie-acceptance hide">
	<div class="cookie-acceptance-container">
		<div class="cookie-acceptance-message">
			<div class="message-container">
				<liferay-ui:message arguments="${themeDisplay.getCompany().getName()}" key="cookie-acceptance-x" />
			</div>
		</div>

		<div class="button-wrapper">
			<a class="btn cookie-acceptance-btn" href="javascript:;"><liferay-ui:message key="accept" /></a>
		</div>
	</div>
</div>

<aui:script sandbox="<%= true %>">
window.addEventListener(
	'DOMContentLoaded',
	function() {
		var cookieAcceptance = document.querySelector('.cookie-acceptance');
		var cookieAcceptanceBtn = document.querySelector('.cookie-acceptance-btn');

		if (cookieAcceptance && cookieAcceptanceBtn && !getCookie('GDPR_ACCEPTED')) {
			cookieAcceptanceBtn.addEventListener(
				'click',
				function(event) {
					var expirationDate = new Date();

					expirationDate.setYear(expirationDate.getFullYear() + 1);

					document.cookie = 'GDPR_ACCEPTED=true; expires=' + expirationDate.toUTCString();

					cookieAcceptance.classList.add('slide-down');

					//Hide the modal after it has gone off screen and clean the slide classes
					setTimeout(function() {
						cookieAcceptance.classList.remove('slide-down');
						cookieAcceptance.classList.remove('slide-up');
						cookieAcceptance.classList.add('hide');
					}, 400);
				}
			);

			cookieAcceptance.classList.remove('hide');

			//We need to set a delay here otherwise the animation won't work
			setTimeout(function() {
				cookieAcceptance.classList.add('slide-up');
			}, 500);
		}
	}
);

function getCookie(name) {
	var value = "; " + document.cookie;
	var parts = value.split("; " + name + "=");
	if (parts.length == 2) return parts.pop().split(";").shift();
}
</aui:script>