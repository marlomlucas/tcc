
document.addEventListener("DOMContentLoaded", function () {

    const formulario = document.getElementById("produto_form");

    if (!formulario) {
        return;
    }

    const codigoBarra = document.getElementById("id_codigo_barra");

    // Verifica o dígito EAN-8, UPC-A ou EAN-13.
    function verificarDigitoVerificador(codigo) {

        if (![8, 12, 13].includes(codigo.length)) {
            return null;
        }

        if (!/^[0-9]+$/.test(codigo)) {
            return null;
        }

        const digitoInformado = Number(codigo.slice(-1));
        const numeros = codigo.slice(0, -1);

        let soma = 0;

        const invertidos = numeros.split("").reverse();

        invertidos.forEach(function (numero, posicao) {
            const peso = posicao % 2 === 0 ? 3 : 1;
            soma += Number(numero) * peso;
        });

        const digitoCalculado = (10 - (soma % 10)) % 10;

        return digitoInformado === digitoCalculado;
    }

    if (codigoBarra) {

        // Mantém a validação do formato numérico.
        codigoBarra.setAttribute("pattern", "[0-9]{1,20}");

        codigoBarra.setAttribute(
            "title",
            "Informe um código com 1 a 20 dígitos numéricos."
        );

        codigoBarra.setAttribute("inputmode", "numeric");

        // Cria uma mensagem de aviso abaixo do campo.
        const aviso = document.createElement("p");

        aviso.id = "aviso-digito-verificador";
        aviso.style.color = "#925800";
        aviso.style.marginTop = "8px";
        aviso.setAttribute("role", "status");
        aviso.hidden = true;

        codigoBarra.insertAdjacentElement("afterend", aviso);

        function atualizarAviso() {

            const codigo = codigoBarra.value.trim();

            const resultado = verificarDigitoVerificador(codigo);

            if (resultado === false) {

                aviso.textContent =
                    "Atenção: o dígito verificador não corresponde " +
                    "ao padrão EAN/UPC. Confira o código informado. " +
                    "** Em caso de código interno, pode prosseguir normalmente.";

                aviso.hidden = false;

            } else {

                aviso.textContent = "";
                aviso.hidden = true;
            }
        }

        // Atualiza a mensagem enquanto o código é editado.
        codigoBarra.addEventListener("input", atualizarAviso);

        // Verifica também ao sair do campo.
        codigoBarra.addEventListener("blur", atualizarAviso);

        // Verifica valores já preenchidos na edição.
        atualizarAviso();
    }

    // Preserva a validação que evita recarregar o formulário.
    formulario.addEventListener("submit", function (evento) {

        if (!formulario.checkValidity()) {

            evento.preventDefault();

            formulario.reportValidity();
        }
    });

});
