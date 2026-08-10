$(function () {
    $('#upload-form').on('submit', function (event) {
        event.preventDefault();

        var fileInput = document.getElementById('file');
        if (!fileInput.files.length) {
            return;
        }

        var formData = new FormData();
        formData.append('file', fileInput.files[0]);

        var $feedback = $('#upload-feedback');
        var $submit = $('#upload-submit');
        $submit.prop('disabled', true);

        $.ajax({
            url: '/api/documents',
            type: 'POST',
            data: formData,
            processData: false,
            contentType: false
        }).done(function (response) {
            $feedback.removeClass('d-none alert-danger').addClass('alert-success')
                .text('Upload realizado com sucesso. Documento "' + response.fileName + '" está com status ' + response.status + '.');
            $('#upload-form')[0].reset();
        }).fail(function (xhr) {
            var message = (xhr.responseJSON && xhr.responseJSON.message) ? xhr.responseJSON.message : 'Falha ao enviar o documento.';
            $feedback.removeClass('d-none alert-success').addClass('alert-danger').text(message);
        }).always(function () {
            $submit.prop('disabled', false);
        });
    });
});
