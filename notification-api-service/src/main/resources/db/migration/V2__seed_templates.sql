INSERT INTO notification_templates (id, subject_template, body_template, category, mandatory) VALUES
 ('WELCOME',          'Welcome, {{name}}!',                     'Hi {{name}}, your account is ready. Have a look around.',                      'SUCCESS', false),
 ('ORDER_SHIPPED',    'Order {{orderId}} has shipped',          'Your order {{orderId}} is on its way and should arrive by {{eta}}.',           'INFO',    false),
 ('PAYMENT_FAILED',   'Payment failed for order {{orderId}}',   'We could not charge your card for order {{orderId}}. Please update payment.',  'WARNING', false),
 ('PASSWORD_CHANGED', 'Your password was changed',              'Your password was changed from {{device}}. If this was not you, act now.',     'ERROR',   true);
