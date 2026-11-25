window.addEventListener('load', () => {
  window.ui = SwaggerUIBundle({
    url: new URL('openapi.yaml', window.location.href).href,
    dom_id: '#swagger-ui',
    deepLinking: true,
    filter: true,
    validatorUrl: null,
    withCredentials: true,
    persistAuthorization: false,
    defaultModelsExpandDepth: 0,
    displayRequestDuration: true,
    presets: [SwaggerUIBundle.presets.apis],
  });
});
