const express = require('express');
const bodyParser = require('body-parser');

const app = express();

app.use(bodyParser.text({type: "text/plain"}));

app.post('/', (req, res) => {
  console.log(req.body);
  res.send('ok');
});

app.listen(3000, () => console.log('echo logger listening on port 3000'));